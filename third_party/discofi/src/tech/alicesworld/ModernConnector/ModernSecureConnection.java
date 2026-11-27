/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tech.alicesworld.ModernConnector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
// import java.security.SecureRandom;
import javax.microedition.io.Connector;
import javax.microedition.io.SecureConnection;
import javax.microedition.io.SecurityInfo;
import javax.microedition.io.SocketConnection;
// import org.bouncycastle.crypto.tls.TlsClientProtocol;
import fi.gtrxac.tinytls.*;

/**
 *
 * @author NealShah
 */
public class ModernSecureConnection implements SecureConnection {
    private TinyTLS tls;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    
    public ModernSecureConnection(String serverHost, int port) throws IOException {          
        tls = new TinyTLS(null);

        tls.connect(serverHost, port);

        inputStream = new DataInputStream(tls.openInputStream());
        outputStream = new DataOutputStream(tls.openOutputStream());
    }
    
    public SecurityInfo getSecurityInfo() throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public int getLocalPort() throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public int getPort() throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public int getSocketOption(byte option) throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public String getAddress() throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public String getLocalAddress() throws IOException {
        throw new RuntimeException("Not supported yet.");
    }

    public void setSocketOption(byte option, int value) throws IOException {
    }

    public void close() throws IOException {
        tls.close();
    }
    
    public DataInputStream openDataInputStream() throws IOException {
        return inputStream;
    }

    public InputStream openInputStream() throws IOException {
        return inputStream;
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return outputStream;
    }

    public OutputStream openOutputStream() throws IOException {
        return outputStream;
    }
    
}