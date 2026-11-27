package fi.gtrxac.discord;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;

/**
 * Utility method to make a circle cut-out for an image (useful for profile pictures).
 * From Discord J2ME.
 * 
 * Example usage (ideally in a separate thread):
 * 
 *  boolean useAlphaBlend = (myDisplay.numAlphaLevels() > 2);
 *  Image myImageCutout = CircleCutout.make(myImage, useAlphaBlend);
 */
public class CircleCutout {
    private static final int[] alphaAndValues = {0x00000000, 0x3FFFFFFF, 0x7FFFFFFF, 0xBFFFFFFF, 0xFFFFFFFF};
    
    /**
     * `smooth` should generally be enabled when display supports
     * alpha blending (Display.numAlphaLevels() > 2)
     */
    public static Image make(Image img, boolean smooth) {
        int size = img.getWidth();

        int[] imageData = new int[size*size];
        img.getRGB(imageData, 0, size, 0, 0, size, size);

        // Modify the image data to turn the pixels outside the circle transparent
        if (smooth) {
            // Alpha blending supported and enabled - use anti-aliasing (double resolution circle
            // buffer where 4 neighboring pixels are calculated together -> 5 alpha levels)
            int[] circleData = createCircleBuf(size*2);

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    imageData[y*size + x] &= getCircleBufAlpha(circleData, size, x, y);
                }
            }
        } else {
            // No alpha blending - do basic non-anti-aliased circle cutout
            int[] circleData = createCircleBuf(size);

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (circleData[y*size + x] == 0xFF000000) continue;
                    imageData[y*size + x] = 0;
                }
            }
        }
        // Create a new image from the modified image data
        return Image.createRGBImage(imageData, size, size, true);
    }

    private static int[] createCircleBuf(int size) {
        // Draw circle to a secondary buffer (bg = white, circle = black)
        Image circleImage = Image.createImage(size, size);
        Graphics cg = circleImage.getGraphics();
        cg.setColor(0);
        cg.fillArc(0, 0, size, size, 0, 360);

        // Get the bitmap data of the circle
        int[] circleData = new int[size*size];
        circleImage.getRGB(circleData, 0, size, 0, 0, size, size);

        return circleData;
    }

    private static int getCircleBufAlpha(int[] buf, int size, int x, int y) {
        int alpha = 0;
        if (buf[(y*4)*size + (x*2)] == 0xFF000000) alpha++;
        if (buf[(y*4)*size + (x*2) + 1] == 0xFF000000) alpha++;
        if (buf[(y*4 + 2)*size + (x*2)] == 0xFF000000) alpha++;
        if (buf[(y*4 + 2)*size + (x*2) + 1] == 0xFF000000) alpha++;
        return alphaAndValues[alpha];
    }
}