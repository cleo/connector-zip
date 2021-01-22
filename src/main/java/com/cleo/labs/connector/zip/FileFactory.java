package com.cleo.labs.connector.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import com.cleo.connector.shell.interfaces.IConnectorAction;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.util.zip.ThreadedZipDirectoryInputStream.Copier;
import com.google.common.io.ByteStreams;

public interface FileFactory {
    public void setup(IConnectorHost host, IConnectorAction action);
    public void setSourceAndDest(String source, String dest, int col, Consumer<String> debug);

    public File getFile(String filename);
    default File getFile(String filename, String[] subpath) {
        StringBuilder s = new StringBuilder().append(filename);
        if (s.charAt(s.length()-1) != '/') {
            s.append('/');
        }
        for (String element : subpath) {
            s.append(element).append('/');
        }
        s.setLength(s.length()-1);
        return getFile(s.toString());
    }

    public InputStream getInputStream(File file) throws IOException;
    default InputStream getInputStream(String filename) throws IOException {
        return getInputStream(getFile(filename));
    }

    default Copier getCopier() {
        return (from,to) -> {
            InputStream is = getInputStream(from.file());
            ByteStreams.copy(is, to);
            to.flush();
        };
    }

    public OutputStream getOutputStream(File file, long modtime) throws IOException;
    default OutputStream getOutputStream(String filename, long modtime) throws IOException {
        return getOutputStream(getFile(filename), modtime);
    }
}
