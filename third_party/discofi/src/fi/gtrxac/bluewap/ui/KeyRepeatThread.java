package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.*;

public class KeyRepeatThread extends Thread {
    public static volatile int activeKey;
    public static final KeyRepeatThread instance = new KeyRepeatThread();
    
    public void run() {
        while (true) {
            try {
                synchronized (this) {
                    wait(0);
                }
            }
            catch (InterruptedException e) {}

            while (true) {
                long curr = System.currentTimeMillis();
                if (curr >= AppCanvas.beginRepeatTime) break;
                Util.sleep((int) (AppCanvas.beginRepeatTime - curr));
            }

            while (activeKey != 0 && AppCanvas.instance.isShown()) {
                try {
                    AppCanvas.instance.keyEvent(activeKey);
                    AppBase.repaint();
                    Thread.sleep(50);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}