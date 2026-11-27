package fi.gtrxac.bluewap;

import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.*;

public class Util {
    // _________________________________________________________________________
    //
    //  Text encoding
    // _________________________________________________________________________
    //

	public static byte[] stringToBytes(String str) {
		return stringToBytes(str, null);
	}

	public static byte[] stringToBytes(String str, String charset) {
		if (charset == null || charset.length() == 0) {
			charset = "UTF-8";
		}
		try {
			return str.getBytes(charset);
		}
		catch (Exception e) {
			return str.getBytes();
		}
	}

	public static String bytesToString(byte[] bytes) {
		return bytesToString(bytes, null);
	}

	public static String bytesToString(byte[] bytes, String charset) {
		if (charset == null || charset.length() == 0) {
			charset = "UTF-8";
		}
		try {
			return new String(bytes, charset);
		}
		catch (Exception e) {
			return new String(bytes);
		}
	}

	/**
	 * Get the name of an (ASCII-compatible) encoding that is supported by the platform, preferring the encoding given as input.
	 */
    public static String getSupportedEncoding(String preferredEncoding) {
        String[] testEncs = { preferredEncoding, "UTF-8", "ISO-8859-1", "US_ASCII" };
		byte[] testBytes = "a".getBytes();

        for (int i = 0; i < testEncs.length; i++) {
            try {
				new String(testBytes, testEncs[i]);
                return testEncs[i];
            }
            catch (UnsupportedEncodingException e) {}
        }
        return "ASCII";  // last resort
    }

	/**
	 * Get the character set/encoding specified in an HTTP Content-Type header.
	 */
	public static String getCharsetFromContentType(String contentType) {
		if (contentType == null) return null;

		int charsetIndex = contentType.toLowerCase().indexOf("charset=");
		if (charsetIndex == -1) return null;

		int start = charsetIndex + "charset=".length();
		int end = contentType.length();
		int semicolonIndex = contentType.indexOf(';', start);
		if (semicolonIndex != -1) end = semicolonIndex;

		String charset = contentType.substring(start, end).trim();
		if (charset.length() > 1 && charset.startsWith("\"") && charset.endsWith("\"")) {
			charset = charset.substring(1, charset.length() - 1);
		}
		return charset;
	}

    // _________________________________________________________________________
    //
    //  String utilities
    // _________________________________________________________________________
    //

	public static String replace(String str, String from, String to) {
		int j = str.indexOf(from);
		if (j == -1)
			return str;
		final StringBuffer sb = new StringBuffer();
		int k = 0;
		for (int i = from.length(); j != -1; j = str.indexOf(from, k)) {
			sb.append(str.substring(k, j)).append(to);
			k = j + i;
		}
		sb.append(str.substring(k, str.length()));
		return sb.toString();
	}

	public static String[] split(String str, String delimiter) {
		if (str == null || str.length() == 0) {
			return new String[0];
		}
		Vector parts = _split(str, delimiter);
		String[] result = new String[parts.size()];
		parts.copyInto(result);
		return result;
	}

	public static Vector splitVec(String str, String delimiter) {
		if (str == null || str.length() == 0) {
			return new Vector();
		}
		return _split(str, delimiter);
	}

	private static Vector _split(String str, String delimiter) {
		Vector parts = new Vector();
		int start = 0;
		int index;
		
		while ((index = str.indexOf(delimiter, start)) != -1) {
			parts.addElement(str.substring(start, index));
			start = index + delimiter.length();
		}
		
		// Add the last part
		parts.addElement(str.substring(start));

		return parts;
	}

    public static String sanitizeWml(String text) {
        text = Util.replace(text, "&", "&amp;");
        text = Util.replace(text, "'", "&apos;");
        text = Util.replace(text, "\"", "&quot;");
        text = Util.replace(text, "<", "&lt;");
        return Util.replace(text, ">", "&gt;");
    }

    private static Font cachedFont;
    private static int cachedMinWidth;
    private static int cachedSpaceWidth;

    /**
     * Get array of text lines to draw (word wrap)
     * Input string should not begin or end with a space or line break (trim the string if needed)
     */
    public static String[] wordWrap(String text, int maxWidth, Font font) {
        if (text == null || text.length() == 0 || text.equals(" ")) {
            return new String[0];
        }
        if (cachedFont != font) {
            cachedFont = font;
            cachedMinWidth = font.charWidth('W') + 2;
            cachedSpaceWidth = font.charWidth(' ');
        }
        if (maxWidth < cachedMinWidth) {
            return new String[0];
        }
        
		// text = replace(text, "\t", "  ");  // comment out if your string will never have tabs
        // text = replace(text, "\r", "");  // comment out if your string will never have carriage returns
        Vector lines = new Vector();

        int lineEnd = text.indexOf('\n');
        if (lineEnd != -1) {
            int lineBegin = 0;
            do {
                lines.addElement(text.substring(lineBegin, lineEnd));
                lineBegin = lineEnd + 1;
                lineEnd = text.indexOf('\n', lineBegin);
            }
            while (lineEnd != -1);

            lines.addElement(text.substring(lineBegin));
        } else {
            lines.addElement(text);
        }

        Vector out = new Vector();
        for (int i = 0; i < lines.size(); i++) {
            String line = (String) lines.elementAt(i);
            int lineLength = line.length();

            if (font.stringWidth(line) > maxWidth) {
                // this line is too long for one screen line, so split it into multiple lines based on word boundaries
                int pos = 0;
                int availableWidth = maxWidth;
                StringBuffer outLine = new StringBuffer();

                loop: while (true) {
                    // get the next word (from cursor position to the next space character, or to the end of the line)
                    int nextSpace = line.indexOf(' ', pos);
                    if (nextSpace == -1) {
                        nextSpace = lineLength;
                    }
                    String thisWord = line.substring(pos, nextSpace);
                    int thisWordWidth = font.stringWidth(thisWord);

                    if (thisWordWidth < availableWidth) {
                        // word fits on the current line
                        outLine.append(thisWord);
                        availableWidth -= thisWordWidth;
                    } else {
                        // word doesn't fit on current line -> finish this line
                        if (outLine.length() != 0) {
                            out.addElement(outLine.toString());
                            outLine.setLength(0);
                        }

                        if (thisWordWidth < maxWidth) {
                            // word fits on one line -> add the word to the next line
                            outLine.append(thisWord);
                            availableWidth = maxWidth - thisWordWidth;
                        } else {
                            // word is too long to fit on one line -> split the word
                            for (int c = thisWord.length() - 1; c >= 0; c--) {
                                String splitWord = thisWord.substring(0, c);
    
                                if (font.stringWidth(splitWord) < maxWidth) {
                                    out.addElement(splitWord);
                                    pos += c;
                                    break;
                                }
                            }
                            availableWidth = maxWidth;
                            continue;
                        }
                    }

                    // skip past this word
                    pos += thisWord.length();

                    while (true) {
                        if (pos >= lineLength) break loop;

                        // add space(s) to the end of the current line (a line will never begin with a space)
                        if (line.charAt(pos) != ' ') break;
                        outLine.append(' ');
                        availableWidth -= cachedSpaceWidth;
                        pos++;
                    }
                }
                // add the last remaining line to the output if needed
                if (outLine.length() != 0) {
                    out.addElement(outLine.toString());
                }
            } else {
                // this whole line fits on one screen line, so add it as-is
                out.addElement(line);
            }
        }
        String[] arr = new String[out.size()];
        out.copyInto(arr);
        return arr;
    }

	public static boolean charIsWhitespace(char c) {
		return c == ' ' || c == '\r' || c == '\n' || c == '\t';
	}

    public static String removeDuplicateWhitespace(String text) {
        boolean atWhitespace = false;

        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (charIsWhitespace(c)) {
                if (atWhitespace) {
                    text = text.substring(0, i) + text.substring(i + 1);
                } else {
                    text = text.substring(0, i) + " " + text.substring(i + 1);
                    atWhitespace = true;
					i++;
                }
            } else {
                atWhitespace = false;
				i++;
            }
        }
        return text;
    }

    public static String trimLeft(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (charIsWhitespace(c)) continue;
            return text.substring(i);
        }
        return "";
    }

    public static String trimRight(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (charIsWhitespace(c)) continue;
            return text.substring(0, i + 1);
        }
        return "";
    }

	public static String stringToLength(String str, int length) {
		return (str.length() >= length && length > 3) ?
			str.substring(0, length - 3) + "..." : 
			str;
	}

	public static String stringToWidth(String str, Font font, int area) {
        if (font.stringWidth(str) < area) return str;

        area -= font.stringWidth("...");
		
        // Reduce string length until it fits in the area
        while (font.stringWidth(str) >= area && str.length() > 0) {
            str = str.substring(0, str.length() - 1);
        }

		// For a little bit nicer display, remove one trailing space if there is one
		if (str.length() > 0 && charIsWhitespace(str.charAt(str.length() - 1))) {
            str = str.substring(0, str.length() - 1);
		}

        return str + "...";
	}

    public static String formatTimestamp(long timestamp, boolean use12hTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        int todayMonth = cal.get(Calendar.MONTH);
        int todayYear = cal.get(Calendar.YEAR);

        cal.setTime(new Date(timestamp));
        int stampDay = cal.get(Calendar.DAY_OF_MONTH);
        int stampMonth = cal.get(Calendar.MONTH);
        int stampYear = cal.get(Calendar.YEAR);

        StringBuffer out = new StringBuffer();

        if (todayDay != stampDay || todayMonth != stampMonth || todayYear != stampYear) {
            // Timestamp does not point to today -> show date in day/month format
            // Leading zeros in numbers are optional, here I left them out for the sake of compactness

            out.append(stampDay).append('/').append(stampMonth + 1);

            // With leading zeros:
            // stampMonth++;
            // if (stampDay < 10) time.append('0');
            // out.append(stampDay).append('/');
            // if (stampMonth < 10) time.append('0');
            // time.append(stampMonth);
        }
        else {
            // Today -> show time in hour:minute format
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            if (use12hTime) {
                char period = hour < 12 ? 'A' : 'P';

                // Convert hours to 12-hour format
                hour = hour % 12;
                if (hour == 0) {
                    hour = 12; // 12 AM or 12 PM
                }

                out.append(hour).append(':');
                if (minute < 10) out.append('0');
                out.append(minute).append(period);
            }
            else {
                out.append(hour).append(':');
                if (minute < 10) out.append('0');
                out.append(minute);
            }
        }
        return out.toString();
    }

    // _________________________________________________________________________
    //
    //  RMS
    // _________________________________________________________________________
    //

	public static void setOrAddRecord(RecordStore rms, int index, String data) throws Exception {
		setOrAddRecord(rms, index, stringToBytes(data));
	}

	public static void setOrAddRecord(RecordStore rms, int index, byte[] data) throws Exception {
		if (rms.getNumRecords() >= index) {
			rms.setRecord(index, data, 0, data.length);
		} else {
			rms.addRecord(data, 0, data.length);
		}
	}

	public static void closeRecordStore(RecordStore rms) {
		try {
			rms.closeRecordStore();
		}
		catch (Exception e) {}
	}

    // _________________________________________________________________________
    //
    //  Misc
    // _________________________________________________________________________
    //

	// https://github.com/gtrxAC/discord-j2me/pull/5/commits/193c63f6a00b8e24da7a3582e9d1a92522f9940e
	public static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize) throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if(count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if(buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}

	public static byte[] readBytes(InputStream inputStream) throws IOException {
		return readBytes(inputStream, 0, 1024, 2048);
	}

	public static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		}
		catch (Exception e) {}
	}

	/**
	 * Get the dimensions that an image should be resized to
	 * in order to fit the specified maximum dimensions and keep the original aspect ratio.
	 * 
	 * If mustUpscale is false, the original image size is kept if it's smaller than the max dimensions.
	 * 
	 * Returns array [width, height]
	 */
	public static int[] resizeFit(int imgW, int imgH, int maxW, int maxH, boolean mustUpscale) {
		int imgAspect = imgW*1000 / imgH;
		int maxAspect = maxW*1000 / maxH;
		int width, height;

		if (!mustUpscale && imgW <= maxW && imgH <= maxH) {
			width = imgW;
			height = imgH;
		}
		else if (imgAspect > maxAspect) {
			width = maxW;
			height = (maxW*1000)/imgAspect;
		}
		else {
			height = maxH;
			width = (maxH*imgAspect)/1000;
		}

		return new int[] {width, height};
	}

	public static int[] resizeFit(int imgW, int imgH, int maxW, int maxH) {
		return resizeFit(imgW, imgH, maxW, maxH, false);
	}

    // _________________________________________________________________________
    //
    //  Color utilities
    // _________________________________________________________________________
    //

	/**
	 * Split RGB color to its components.
	 * @param color RGB color value
	 * @return An array of three integers where the first is the red value of the given color, second green, and third blue.
	 */
	public static int[] splitRGB(int color) {
		return new int[] {
			(color & 0x00FF0000) >> 16,
			(color & 0x0000FF00) >> 8,
			(color & 0x000000FF)
		};
	}
	
	/**
	 * Blend colors A and B. Alpha is disregarded.
	 * @param a First RGB color value to be blended
	 * @param b Second RGB color value to be blended
	 * @param aRatio The ratio of A to B in increments of 10%, for example, with aRatio = 7, the resulting color will be a blend of 70% A and 30% B.
	 * @return The blended RGB color value
	 */
	public static int blend(int a, int b, int aRatio) {
		int[] as = splitRGB(a);
		int[] bs = splitRGB(b);

		int bRatio = 10 - aRatio;
		int cR = (as[0]*aRatio/10 + bs[0]*bRatio/10) & 0xFF;
		int cG = (as[1]*aRatio/10 + bs[1]*bRatio/10) & 0xFF;
		int cB = (as[2]*aRatio/10 + bs[2]*bRatio/10) & 0xFF;

		return (cR << 16) | (cG << 8) | cB;
	}

	public static int contrast(int color, int compare) {
		int[] colorSplit = splitRGB(color);
		int[] compareSplit = splitRGB(compare);

		return
			Math.abs(colorSplit[0] - compareSplit[0]) +
			Math.abs(colorSplit[1] - compareSplit[1]) +
			Math.abs(colorSplit[2] - compareSplit[2]);
	}

	/**
	 * Get which of the colors (A or B) has a higher contrast against the 'compare' color. Alpha is disregarded.
	 */
	public static int higherContrast(int a, int b, int compare) {
		if (contrast(b, compare) > contrast(a, compare)) return b;
		return a;
	}

    // _________________________________________________________________________
    //
    //  URL encode/decode
    // _________________________________________________________________________
    //

	// https://github.com/phd051199/MIDPlay/blob/main/src/Utils.java#L125

  	private static final String HEX_DIGITS = "0123456789ABCDEF";

	public static String urlEncode(String text) {
		if (text == null) return "";

		try {
			byte[] bytes = text.getBytes("UTF-8");
			StringBuffer result = new StringBuffer(bytes.length + (bytes.length >> 1));

			for (int i = 0; i < bytes.length; i++) {
				int b = bytes[i] & 0xFF;
				char c = (char) b;

				if (c == ' ') {
					result.append('+');
				}
				else if (isUrlSafeCharacter(c)) {
					result.append(c);
				}
				else {
					result.append('%')
						.append(HEX_DIGITS.charAt((b >> 4) & 0xF))
						.append(HEX_DIGITS.charAt(b & 0xF));
				}
			}
			return result.toString();
		}
		catch (UnsupportedEncodingException e) {
			return "";
		}
	}

	private static boolean isUrlSafeCharacter(char c) {
		return (c >= 'A' && c <= 'Z')
			|| (c >= 'a' && c <= 'z')
			|| (c >= '0' && c <= '9')
			|| c == '-'
			|| c == '_'
			|| c == '.'
			|| c == '~';
	}

    public static String urlDecode(String value) {
        if (value == null) return "";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '+') {
                bytes.write(32);
            }
			else if (c == '%' && i + 2 < value.length()) {
                int hi = hexToInt(value.charAt(i + 1));
                int lo = hexToInt(value.charAt(i + 2));

                if (hi >= 0 && lo >= 0) {
                    bytes.write((hi << 4) | lo);
                    i += 2;
                }
				else {
                    bytes.write((byte) c);
                }
            }
			else {
                bytes.write((byte) c);
            }
        }

		return bytesToString(bytes.toByteArray());
    }

    // _________________________________________________________________________
    //
    //  Hex - bytearray conversions
    // _________________________________________________________________________
    //

    private static final char[] HEX_CHARS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    
	/**
	 * Convert hex string to byte array.
	 * Input string can only have hex characters, no whitespace or other characters.
	 * Case insensitive.
	 */
    public static byte[] hexToBytes(String hex) {
        int count = hex.length() / 2;
        byte[] result = new byte[count];

        for (int i = 0; i < hex.length(); i += 2) {
            int hi = hexToInt(hex.charAt(i));
            int lo = hexToInt(hex.charAt(i + 1));

            result[i/2] = (byte) ((hi << 4) | lo);
        }

        return result;
    }

	/**
	 * Get integer corresponding to hex digit (e.g. 'f' -> 15)
	 * Returns -1 if input is not a hex digit.
	 * Case insensitive.
	 */
    public static int hexToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return -1;
    }

	/**
	 * Get hex digit corresponding to integer (e.g. 15 to 'f').
	 */
	public static char intToHex(int x) {
        if (x < 10) {
            return (char) ('0' + x);
        }
        return (char) ('a' + x - 10);
	}

	/**
	 * Convert byte array to hex string (lowercase, no separators or other characters)
	 */
    public static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();

        for (int i = 0; i < bytes.length; i++) {
            int hi = (bytes[i] & 0xF0) >> 4;
            int lo = bytes[i] & 0x0F;

            result.append(HEX_CHARS[hi]).append(HEX_CHARS[lo]);
        }

        return result.toString();
    }
}