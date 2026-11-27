package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import tube42.lib.imagelib.ImageUtils;

/**
 * Item for selectable lists.
 * 
 * For the correct layout, a BlankItem(0) should be added
 * before every cluster of ListItems.
 */
public class ListItem extends Item {
    public String text;
    private String displayText;
    public Object object;

    private static int paddingY;
    private static int imageHash;
    private static Image arrowImage;

    public ListItem(String text) {
        super(true);
        this.text = text;
    }

    /**
     * 'object' can be any application-defined object that this ListItem refers to.
     * It is simply stored by this class for later retrieval e.g. when the item is selected.
     */
    public ListItem(String text, Object object) {
        this(text);
        this.object = object;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        if (highlighted) {
            g.setColor(Colors.listItemBackgroundSel);
            g.fillRect(0, -screen.itemPadding, width, height + 1);

            drawHighlight(g, 0, -screen.itemPadding, width, height + 1);

            g.setColor(Colors.listItemTextSel);
        }
        else {
            g.setColor(Colors.listItemText);
        }

        g.setFont(Fonts.plain);
        g.drawString(displayText, Fonts.height/4, paddingY, 0);

        if (!highlighted) {
            g.setColor(Colors.spacerItem);
            g.drawLine(0, height - 1, width - 1, height - 1);
        }

        g.drawImage(arrowImage, width - Fonts.height/3, paddingY,
            Graphics.TOP | Graphics.RIGHT);
    }

    public void recalc(int width, ListScreen screen, Item aboveItem) {
        paddingY = Platform.isTouch ? Fonts.height/3 : Fonts.height/6;

        height = Fonts.height + paddingY*2 + screen.itemPadding + 1;

        int textWidth = width - Fonts.height/4 - Fonts.height - Fonts.height/3;
        displayText = Util.stringToWidth(text, Fonts.plain, textWidth);

        int newImageSize = Fonts.height;
        int newImageHash = newImageSize + Colors.listItemArrow;

        if (imageHash != newImageHash) {
            arrowImage = null;
            arrowImage = createArrowImage(newImageSize, Colors.listItemArrow);
            imageHash = newImageHash;
        }
    }

    private Image createArrowImage(int size, int color) {
        int renderSize = (size*Platform.vectorRenderScale + 13)/14*14;
        int blockSize = renderSize/14;

        Image result = Image.createImage(renderSize, renderSize);
        Graphics g = result.getGraphics();

        // background
        g.setColor(0xFF0000);
        g.fillRect(0, 0, renderSize, renderSize);

        // outline
        g.setColor(color);
        g.fillArc(blockSize, blockSize, blockSize*12, blockSize*12, 0, 360);

        // fill insides
        g.setColor(0xFF0000);
        g.fillArc(blockSize*2, blockSize*2, blockSize*10, blockSize*10, 0, 360);

        // triangle
        g.setColor(color);
        g.fillTriangle(
            blockSize*6, blockSize*4,
            blockSize*9, blockSize*7,
            blockSize*6, blockSize*10);

        // triangle with background color to make it an arrow
        int thickness = blockSize*3/2;
        g.setColor(0xFF0000);
        g.fillTriangle(
            blockSize*6, blockSize*4 + thickness,
            blockSize*9 - thickness, blockSize*7,
            blockSize*6, blockSize*10 - thickness);
        
        // make transparent
        int[] rgb = new int[renderSize*renderSize];
        result.getRGB(rgb, 0, renderSize, 0, 0, renderSize, renderSize);
        result = null;
        int topLeft = rgb[0];  // color of an unpainted pixel

        for (int i = 0; i < rgb.length; i++) {
            // Make every unpainted (white) pixel transparent (0 alpha).
            // Using the same color as the button's outline to avoid ugly outlines
            if (rgb[i] == topLeft) {
                rgb[i] = color & 0x00FFFFFF;
            }
        }

        result = Image.createRGBImage(rgb, renderSize, renderSize, true);

        return ImageUtils.resize(result, size, size, true, true);
    }
}