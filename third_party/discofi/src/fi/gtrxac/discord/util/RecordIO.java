package fi.gtrxac.discord.util;

import java.io.*;
import java.util.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import fi.gtrxac.bluewap.*;

/**
 * Helper class to read/write binary data to an RMS record store.
 * Implement RecordIOImpl in your application and call RecordIO.load() and RecordIO.save().
 */
public class RecordIO {
    public static void load(String rmsName, RecordIOImpl impl) {
        RecordStore rms = null;
        ByteArrayInputStream is = null;
        DataInputStream dis = null;

        try {
            rms = RecordStore.openRecordStore(rmsName, false);
            byte[] record = rms.getRecord(1);
            is = new ByteArrayInputStream(record);
            dis = new DataInputStream(is);

            impl.readData(dis);
        }
        catch (RecordStoreNotFoundException e) {
            // ignore
        }
        catch (EOFException e) {
            // ignore
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            Util.closeRecordStore(rms);
            try { is.close(); } catch (Exception e) {}
            try { dis.close(); } catch (Exception e) {}
        }
    }

    public static void save(String rmsName, RecordIOImpl impl) {
        RecordStore rms = null;
        ByteArrayOutputStream os = null;
        DataOutputStream dos = null;

        try {
            rms = RecordStore.openRecordStore(rmsName, true);
            os = new ByteArrayOutputStream();
            dos = new DataOutputStream(os);

            impl.writeData(dos);
            Util.setOrAddRecord(rms, 1, os.toByteArray());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            Util.closeRecordStore(rms);
            try { os.close(); } catch (Exception e) {}
            try { dos.close(); } catch (Exception e) {}
        }
    }
}