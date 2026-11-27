package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

public class AttachmentScreen extends Screen implements CommandListener {
    public static final int CMD_BACK = 0;
    public static final int CMD_ZOOM_IN = 1;
    public static final int CMD_ZOOM_OUT = 2;

    private static final Command zoomInCommand =
        new Command("Zoom in", Command.SCREEN, CMD_ZOOM_IN);

    private static final Command zoomOutCommand =
        new Command("Zoom out", Command.SCREEN, CMD_ZOOM_OUT);

    private static final int MIN_ZOOM_LEVEL = 1;
    private static final int MAX_ZOOM_LEVEL = 4;

    private Attachment attachment;
    private Image attachmentImage;
    private int offsetX;
    private int offsetY;
    private boolean isLoading;
    private boolean loadFailed;
    private int imageRequestId;

    private int zoomLevel;
    private int zoomWidth;
    private int zoomHeight;

    public AttachmentScreen(Attachment att) {
        super(0);
        setTitle(att.filename);

        attachment = att;
        zoomLevel = MIN_ZOOM_LEVEL;
        // zoomWidth and zoomHeight are set in recalc()
        // where getWidth() and getHeight() are available

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(zoomInCommand);
    }

    private void loadImage() {
        int requestId = ++imageRequestId;
        isLoading = true;
        loadFailed = false;

        HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_ATTACHMENT, this);
        h.attachment = attachment;
        h.attachmentPreviewWidth = zoomWidth;
        h.attachmentPreviewHeight = zoomHeight;
        h.attachmentRequestId = requestId;
        h.start();

        App.repaint();
    }

    // Callback
    public void imageLoaded(int requestId, Image image) {
        if (requestId != imageRequestId) return;

        attachmentImage = image;
        isLoading = false;
        loadFailed = false;
        clampViewOffset();

        App.repaint();
    }

    public void imageLoadFailed(int requestId) {
        if (requestId != imageRequestId) return;

        isLoading = false;
        loadFailed = true;

        App.repaint();
    }

    public void draw(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (attachmentImage != null) {
            g.drawImage(
                attachmentImage,
                getWidth()/2 + offsetX,
                getHeight()/2 + offsetY,
                Graphics.HCENTER | Graphics.VCENTER);
        }

        if (isLoading || loadFailed) {
            String status = loadFailed ? "Failed to load image" : "Loading...";
            int textWidth = Fonts.plain.stringWidth(status);
            int textX = getWidth()/2 - textWidth/2;
            int textY = getHeight()/2 - Fonts.height/2;

            g.fillRect(textX, textY, textWidth, Fonts.height);

            g.setColor(0xFFFFFF);
            g.setFont(Fonts.plain);
            g.drawString(status, textX, textY, 0);
        }
    }

    public void recalc() {
        if (attachmentImage == null && !isLoading && !loadFailed) {
            int[] fitSize = Util.resizeFit(
                attachment.width, attachment.height, getWidth(), getHeight());
                
            zoomWidth = fitSize[0];
            zoomHeight = fitSize[1];

            // Start initial load
            loadImage();
        }
        clampViewOffset();
    }

    public void keyEvent(int keyCode, int gameAction) {
        if (attachmentImage == null) return;

        int increment = Math.min(getWidth(), getHeight())/5;

        switch (gameAction) {
            case Canvas.UP: {
                offsetY += increment;
                break;
            }
            case Canvas.DOWN: {
                offsetY -= increment;
                break;
            }
            case Canvas.LEFT: {
                offsetX += increment;
                break;
            }
            case Canvas.RIGHT: {
                offsetX -= increment;
                break;
            }
            case Canvas.FIRE: {
                zoomIn();
                break;
            }
        }

        clampViewOffset();
    }

    private void clampViewOffset() {
        if (attachmentImage == null) {
            offsetX = 0;
            offsetY = 0;
            return;
        }

        // Allow image to go almost off-screen
        int maxOffsetX = attachmentImage.getWidth()/2 + getWidth()*4/10;
        int maxOffsetY = attachmentImage.getHeight()/2 + getHeight()*4/10;

        if (offsetX < -maxOffsetX) offsetX = -maxOffsetX;
        if (offsetX > maxOffsetX) offsetX = maxOffsetX;
        if (offsetY < -maxOffsetY) offsetY = -maxOffsetY;
        if (offsetY > maxOffsetY) offsetY = maxOffsetY;
    }

    private void zoomIn() {
        addCommand(zoomOutCommand);

        if (zoomLevel < MAX_ZOOM_LEVEL) {
            zoomLevel++;
            zoomWidth *= 2;
            zoomHeight *= 2;
        }

        if (zoomLevel == MAX_ZOOM_LEVEL) {
            removeCommand(zoomInCommand);
        }
        
        loadImage();
    }

    private void zoomOut() {
        addCommand(zoomInCommand);

        if (zoomLevel > MIN_ZOOM_LEVEL) {
            zoomLevel--;
            zoomWidth /= 2;
            zoomHeight /= 2;
        }
        
        if (zoomLevel == MIN_ZOOM_LEVEL) {
            removeCommand(zoomOutCommand);
        }

        loadImage();
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                App.popScreen();
                break;
            }
            case CMD_ZOOM_IN: {
                zoomIn();
                break;
            }
            case CMD_ZOOM_OUT: {
                zoomOut();
                break;
            }
        }
    }

    private int lastPointerX, lastPointerY;

    public void pointerPressed(int x, int y) {
        lastPointerX = x;
        lastPointerY = y; 
    }

    public void pointerDragged(int x, int y) {
        offsetX += (x - lastPointerX);
        offsetY += (y - lastPointerY);
        pointerPressed(x, y);
        clampViewOffset();
    }
}