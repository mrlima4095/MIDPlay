package fi.gtrxac.discord;

import fi.gtrxac.bluewap.*;
import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.Sprite;
import tube42.lib.imagelib.*;

/**
 * Item that shows a loading animation.
 * This is meant to be the only item shown on the screen at a time, and it draws on the center of the screen.
 */
public class LoadingItem extends Item implements Runnable {
    private int curFrame;
    private int animDirection;

    private volatile long threadExpiryTime;
    private volatile boolean threadAlive;

    private static Image animationSheet;
    private static int scale;
    private static int phoneOffset;
    private static int bubbleOffset;

    private static final int[] phoneFrameY = { 6, 28, 50, 85 };
    private static final int[] phoneFrameHeights = { 22, 22, 35, 41 };
    private static final int[] bubbleFrameY = { 126, 134, 148, 167 };
    private static final int[] bubbleFrameHeights = { 8, 14, 19, 24 };

    public LoadingItem() {
        super(false);
        animDirection = 1;
    }

    private static void loadAnimationSheet() {
        try {
            animationSheet = Image.createImage("/l.png");
        }
        catch (Exception e) {
            return;
        }

        if (scale > 1) {
            animationSheet = ImageUtils.resize(animationSheet, 32*scale, 191*scale, false, false);
        }
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        // Draw current animation frame
        int iconHeight = 48*scale;
        int padding = 7*scale;
        int containerHeight = iconHeight + padding + Fonts.height*2;
        int containerBegin = (screen.getHeight() - containerHeight)/2;

        int curPhoneFrame = Math.min(curFrame, 3);

        int phoneX = width/2 + phoneOffset;
        int phoneY = containerBegin + 42*scale;

        // phone bottom part (common across all frames)
        g.drawRegion(
            animationSheet,
            1*scale, 0, 20*scale, 6*scale,
            Sprite.TRANS_NONE,
            phoneX + scale,
            phoneY,
            Graphics.TOP | Graphics.LEFT);

        // phone top part
        g.drawRegion(
            animationSheet,
            0,
            phoneFrameY[curPhoneFrame]*scale,
            32*scale,
            phoneFrameHeights[curPhoneFrame]*scale,
            Sprite.TRANS_NONE,
            phoneX, phoneY,
            Graphics.BOTTOM | Graphics.LEFT);

        int curBubbleFrame = curFrame - 4;

        if (curBubbleFrame >= 0) {
            g.drawRegion(
                animationSheet,
                0,
                bubbleFrameY[curBubbleFrame]*scale,
                31*scale,
                bubbleFrameHeights[curBubbleFrame]*scale,
                Sprite.TRANS_NONE,
                width/2 + bubbleOffset,
                containerBegin + 24*scale,
                Graphics.BOTTOM | Graphics.LEFT);
        }

        g.setColor(Theme.loadingItemText);
        g.setFont(Fonts.plain);
        g.drawString(
            "Loading...",
            width/2,
            containerBegin + iconHeight + padding,
            Graphics.TOP | Graphics.HCENTER);

        // Keep the animation thread alive for longer.
        // This timer system is used so we roughly know when the LoadingItem is
        // no longer being shown, and then the thread will end.
        threadExpiryTime = System.currentTimeMillis() + 1000;

        if (!threadAlive) {
            threadAlive = true;
            new Thread(this).start();
        }
    }

    public void recalc(int width) {
        height = 0;

        int screenHeight = AppCanvas.instance.getHeight();
        int newScale = Math.min(width/270, screenHeight/270) + 1;

        if (animationSheet == null || scale != newScale) {
            scale = newScale;
            loadAnimationSheet();
        }

        // -24 (half of full frame width to center it on screen)
        // -3 (offset to make it look more centered since the phone is more towards the right)
        // 14 (phone offset to make it align with the bubble)
        phoneOffset = (-3 + 14 - 24)*scale;
        bubbleOffset = (-3 - 24)*scale;
    }

    // Icon animation thread
    public void run() {
        long loopStartTime;

        while ((loopStartTime = System.currentTimeMillis()) < threadExpiryTime) {
            App.repaint();
            AppCanvas.instance.serviceRepaints();

            long paintTime = System.currentTimeMillis() - loopStartTime;

            // Sleep based on the frame number that was just drawn (first frame = 167 ms, last frame = 500 ms)
            int sleepTime;
            switch (curFrame) {
                case 0: sleepTime = 167; break;
                case 7: sleepTime = 500; break;
                default: sleepTime = 83; break;
            }
            Util.sleep(sleepTime - (int) paintTime);

            // Go to next animation frame.
            // If end reached, start going through frames in descending order.
            // If beginning reached, start going through frames in ascending order.
            curFrame += animDirection;
            if (curFrame == 7) animDirection = -1;
            if (curFrame == 0) animDirection = 1;
        }

        threadAlive = false;
    }
}