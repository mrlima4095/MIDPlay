//#ifndef NO_FILECONNECTION
package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.Util;
import javax.microedition.io.file.*;
import javax.microedition.lcdui.*;
import java.io.*;

public class TokenImportScreen extends FilePickerScreen {
    public TokenImportScreen() {
        this("file:///");
    }

    protected TokenImportScreen(String currentPath) {
        super("Import token", currentPath);
    }

    protected void directorySelected(String selectedPath) {
        App.pushScreen(new TokenImportScreen(selectedPath));
    }

    // protected boolean fileFilter(String fileName) {
    // }

    protected void handleException(Exception e) {
        new DiscordException(e).show();
    }
    
    protected void fileSelected(FileConnection fc, String selected) {
        InputStream is = null;
        
        try {
            is = fc.openInputStream();

            byte[] tokenBytes = Util.readBytes(is);
            String tokenFull = Util.bytesToString(tokenBytes).trim();
            String tokenTruncated = Util.stringToLength(tokenFull, 200);

            close();

            LoginScreen loginScreen = (LoginScreen) App.getCurrentScreen();
            loginScreen.tokenFileLogin(tokenTruncated);
        }
        catch (Exception e) {
            handleException(e);
        }
        
        try { is.close(); } catch (Exception e) {}
        try { fc.close(); } catch (Exception e) {}
    }
}
//#endif