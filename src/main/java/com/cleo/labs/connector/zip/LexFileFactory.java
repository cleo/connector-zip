package com.cleo.labs.connector.zip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.Consumer;

import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.shell.interfaces.IConnectorAction;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.util.zip.ThreadedZipDirectoryInputStream;
import com.cleo.labs.util.zip.ThreadedZipDirectoryInputStream.Copier;
import com.cleo.lexicom.beans.LexActionBean;
import com.cleo.lexicom.beans.LexFile;
import com.cleo.lexicom.beans.LexHostBean;
import com.cleo.lexicom.beans.LexIO;
import com.cleo.lexicom.beans.LexURIFileUtil;
import com.cleo.lexicom.beans.MacroReplacement;
import com.cleo.lexicom.beans.NetworkFilterInputStream;
import com.cleo.lexicom.beans.NetworkFilterOutputStream;
import com.cleo.versalex.connector.Action;
import com.cleo.versalex.connector.Network;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

public class LexFileFactory implements FileFactory {
    IConnectorHost host = null;
    IConnectorAction action = null;
    Network network = null;
    String inbox = null;
    String source = null;
    String dest = null;
    int col = 0;
    Consumer<String> debug = null;

    public void setup(IConnectorHost host, IConnectorAction action) {
        this.host = host;
        this.action = action;
        this.network = new Network((Action) action);
        try {
            this.inbox = action.getInbox();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setSourceAndDest(String source, String dest, int col, Consumer<String> debug) {
        this.source = source;
        this.dest = dest;
        this.col = col;
        this.debug = debug;
    }

    public LexFile getFile(String filename) {
        try {
            filename = new MacroReplacement().replaceMacrosInString((LexHostBean) host, (LexActionBean) action, filename, source, dest, col);
            LexFile file = new LexFile(filename);
            if (!file.isAbsolute() && !Strings.isNullOrEmpty(this.inbox)) {
                return new LexFile(inbox, filename);
            }
            file.setAllowURI(true);
            if (file.isURIFile()) {
                String invalidUriMsg = LexURIFileUtil.isValidURI(filename, ((LexHostBean) host).getFolder());
                if (!Strings.isNullOrEmpty(invalidUriMsg)) {
                    throw new ConnectorException(invalidUriMsg);
                }
            }
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream(File file) throws IOException {
        try {
            LexFile lexfile = (LexFile)file;
            lexfile.setAllowURI(true);
            NetworkFilterInputStream nfis = new NetworkFilterInputStream(
                    new BufferedInputStream(LexIO.getFileInputStream(lexfile)),
                    (LexActionBean) action,
                    false);
            nfis.setLogTransfers(false);
            nfis.setNoThrottle();
            return nfis;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public Copier getCopier() {
        return (from,to) -> {
            LexFile lexfile = (LexFile)from.file();
            if (lexfile.isNormalFile()) {
                try (FileInputStream fis = new FileInputStream(lexfile.getFile());
                        FileChannel channel = fis.getChannel()) {
                    ByteBuffer buffer = ByteBuffer.allocate(ThreadedZipDirectoryInputStream.DEFAULT_BUFFERSIZE);
                    int n;
                    while ((n = channel.read(buffer)) >= 0) {
                        if (n > 0) {
                            buffer.flip();
                            to.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                        }
                        buffer.clear();
                    }
                }
            } else {
                InputStream is = getInputStream(lexfile);
                ByteStreams.copy(is, to);
                is.close();
                to.flush();
            }
        };
    }

    public OutputStream getOutputStream(File file, long modtime) throws IOException {
        try {
            LexFile lexfile = (LexFile)file;
            lexfile.setAllowURI(true);
            NetworkFilterOutputStream nfos = new NetworkFilterOutputStream(
                    new BufferedOutputStream(LexIO.getFileOutputStream(lexfile)),
                    (LexActionBean) action,
                    false)
            {
                @Override
                public void close() throws IOException {
                    super.close();
                    file.setLastModified(modtime);
                }
            };
            nfos.setLogTransfers(false);
            nfos.setNoThrottle();
            return nfos;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
