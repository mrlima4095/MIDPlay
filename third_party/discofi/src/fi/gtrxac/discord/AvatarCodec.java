package fi.gtrxac.discord;

import javax.microedition.lcdui.Image;

/**
 * Convert 16x16 opaque Image to 4-bit indexed format:
 * 
 *  - 16 RGB565 color entries -> 32 bytes
 *  - 256 4-bit pixels -> 128 bytes
 *  - total -> 160 bytes
 * 
 * Used for compact cache storage of avatars.
 * 
 * Uses median-cut algorithm with K-means refinement, no dithering.
 * 
 * Avatars fetched from Discord CDN, with lowest size and quality options,
 * are generally around 700 bytes for PNG or JPEG, and 300 bytes for WEBP,
 * 150 bytes at the lowest.
 */
public class AvatarCodec {
    private static final int WIDTH = 16;
    private static final int PIXELS = 256;
    private static final int COLORS = 16;
    private static final int PALETTE_BYTES = 32;
    private static final int SIZE = 160;

    public static byte[] encode(Image image) {
        if (image == null || image.getWidth() != WIDTH || image.getHeight() != WIDTH) {
            throw new IllegalArgumentException("Expected 16x16 image");
        }

        int[] pixels = new int[PIXELS];
        image.getRGB(pixels, 0, WIDTH, 0, 0, WIDTH, WIDTH);

        int[] palette = createPalette(pixels);
        refinePalette(pixels, palette);

        // Round palette to the actual stored RGB565 colors
        // before assigning final indices.
        for (int i = 0; i < COLORS; i++) {
            palette[i] = from565(to565(palette[i]));
        }

        byte[] result = new byte[SIZE];
        writePalette(result, palette);
        writeIndices(result, pixels, palette);

        return result;
    }

    public static Image decode(byte[] data) {
        if (data == null || data.length != SIZE) {
            throw new IllegalArgumentException("Expected 160-byte avatar");
        }

        int[] palette = new int[COLORS];
        int[] pixels = new int[PIXELS];

        for (int i = 0; i < COLORS; i++) {
            int off = i * 2;
            int rgb565 =
                ((data[off] & 255) << 8) |
                (data[off + 1] & 255);

            palette[i] = 0xFF000000 | from565(rgb565);
        }

        for (int i = 0; i < PIXELS; i += 2) {
            int packed = data[PALETTE_BYTES + (i >>> 1)] & 255;

            pixels[i] = palette[packed >>> 4];
            pixels[i + 1] = palette[packed & 15];
        }

        return Image.createRGBImage(pixels, WIDTH, WIDTH, false);
    }

    private static int[] createPalette(int[] pixels) {
        int[] order = new int[PIXELS];
        int[] start = new int[COLORS];
        int[] end = new int[COLORS];

        for (int i = 0; i < PIXELS; i++) {
            order[i] = i;
        }

        start[0] = 0;
        end[0] = PIXELS;

        int boxes = 1;

        while (boxes < COLORS) {
            int bestBox = -1;
            int bestScore = -1;
            int bestChannel = 0;

            for (int box = 0; box < boxes; box++) {
                int count = end[box] - start[box];

                if (count < 2) continue;

                int minR = 255, minG = 255, minB = 255;
                int maxR = 0, maxG = 0, maxB = 0;

                for (int j = start[box]; j < end[box]; j++) {
                    int c = pixels[order[j]];
                    int r = (c >>> 16) & 255;
                    int g = (c >>> 8) & 255;
                    int b = c & 255;

                    if (r < minR) minR = r;
                    if (r > maxR) maxR = r;
                    if (g < minG) minG = g;
                    if (g > maxG) maxG = g;
                    if (b < minB) minB = b;
                    if (b > maxB) maxB = b;
                }

                int range = maxR - minR;
                int channel = 16;

                if (maxG - minG > range) {
                    range = maxG - minG;
                    channel = 8;
                }

                if (maxB - minB > range) {
                    range = maxB - minB;
                    channel = 0;
                }

                int score = range * count;

                if (score > bestScore) {
                    bestScore = score;
                    bestBox = box;
                    bestChannel = channel;
                }
            }

            if (bestBox < 0) break;

            int lo = start[bestBox];
            int hi = end[bestBox];

            sortByChannel(pixels, order, lo, hi, bestChannel);

            int middle = (lo + hi) >>> 1;

            start[boxes] = middle;
            end[boxes] = hi;
            end[bestBox] = middle;

            boxes++;
        }

        int[] palette = new int[COLORS];

        for (int box = 0; box < boxes; box++) {
            palette[box] = averageBox(pixels, order, start[box], end[box]);
        }

        for (int i = boxes; i < COLORS; i++) {
            palette[i] = palette[0];
        }

        return palette;
    }

    private static void sortByChannel(int[] pixels, int[] order, int lo, int hi, int shift) {

        // Insertion sort; groups contain at most 256 pixels.
        for (int i = lo + 1; i < hi; i++) {
            int value = order[i];
            int channel =
                (pixels[value] >>> shift) & 255;

            int j = i - 1;

            while (j >= lo &&
                   ((pixels[order[j]] >>> shift) & 255)
                   > channel) {
                order[j + 1] = order[j];
                j--;
            }

            order[j + 1] = value;
        }
    }

    private static int averageBox(int[] pixels, int[] order, int lo, int hi) {
        int sumR = 0;
        int sumG = 0;
        int sumB = 0;
        int count = hi - lo;

        if (count == 0) return 0;

        for (int i = lo; i < hi; i++) {
            int c = pixels[order[i]];

            sumR += (c >>> 16) & 255;
            sumG += (c >>> 8) & 255;
            sumB += c & 255;
        }

        int r = (sumR + count / 2) / count;
        int g = (sumG + count / 2) / count;
        int b = (sumB + count / 2) / count;

        return (r << 16) | (g << 8) | b;
    }

    private static void refinePalette(int[] pixels, int[] palette) {

        int[] sumR = new int[COLORS];
        int[] sumG = new int[COLORS];
        int[] sumB = new int[COLORS];
        int[] counts = new int[COLORS];

        for (int iteration = 0; iteration < 3; iteration++) {
            for (int k = 0; k < COLORS; k++) {
                sumR[k] = 0;
                sumG[k] = 0;
                sumB[k] = 0;
                counts[k] = 0;
            }

            for (int i = 0; i < PIXELS; i++) {
                int nearest = nearestColor(pixels[i], palette);

                int c = pixels[i];

                sumR[nearest] += (c >>> 16) & 255;
                sumG[nearest] += (c >>> 8) & 255;
                sumB[nearest] += c & 255;
                counts[nearest]++;
            }

            for (int k = 0; k < COLORS; k++) {
                int count = counts[k];

                if (count > 0) {
                    int r = (sumR[k] + count / 2) / count;
                    int g = (sumG[k] + count / 2) / count;
                    int b = (sumB[k] + count / 2) / count;

                    palette[k] = (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    private static int nearestColor(int color, int[] palette) {
        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;

        int nearest = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int k = 0; k < COLORS; k++) {
            int p = palette[k];

            int dr = r - ((p >>> 16) & 255);
            int dg = g - ((p >>> 8) & 255);
            int db = b - (p & 255);

            int distance =
                3 * dr * dr +
                4 * dg * dg +
                2 * db * db;

            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = k;
            }
        }

        return nearest;
    }

    private static void writePalette(byte[] result, int[] palette) {
        for (int i = 0; i < COLORS; i++) {
            int c = to565(palette[i]);
            int off = i * 2;

            result[off] = (byte)(c >>> 8);
            result[off + 1] = (byte)c;
        }
    }

    private static void writeIndices(byte[] result, int[] pixels, int[] palette) {
        for (int i = 0; i < PIXELS; i += 2) {
            int a = nearestColor(pixels[i], palette);
            int b = nearestColor(pixels[i + 1], palette);

            result[PALETTE_BYTES + (i >>> 1)] =
                (byte)((a << 4) | b);
        }
    }

    private static int to565(int rgb) {
        int r = (rgb >>> 19) & 31;
        int g = (rgb >>> 10) & 63;
        int b = (rgb >>> 3) & 31;

        return (r << 11) | (g << 5) | b;
    }

    private static int from565(int rgb565) {
        int r = (rgb565 >>> 11) & 31;
        int g = (rgb565 >>> 5) & 63;
        int b = rgb565 & 31;

        // Expand bits to approximate full 8-bit RGB.
        r = (r << 3) | (r >>> 2);
        g = (g << 2) | (g >>> 4);
        b = (b << 3) | (b >>> 2);

        return (r << 16) | (g << 8) | b;
    }
}