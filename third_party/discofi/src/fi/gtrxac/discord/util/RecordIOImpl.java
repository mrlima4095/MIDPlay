package fi.gtrxac.discord.util;

import java.io.*;

public interface RecordIOImpl {
    public void readData(DataInputStream dis) throws IOException;
    public void writeData(DataOutputStream dos) throws IOException;
}