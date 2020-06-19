package com.cleo.labs.connector.zip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.helper.Logger;
import com.cleo.connector.shell.interfaces.IConnectorAction;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.lexicom.beans.LexActionBean;
import com.cleo.lexicom.beans.LexFile;
import com.cleo.lexicom.beans.LexHostBean;
import com.cleo.lexicom.beans.LexIO;
import com.cleo.lexicom.beans.LexURIFileUtil;
import com.cleo.lexicom.beans.MacroReplacement;
import com.cleo.lexicom.beans.NetworkFilterOutputStream;
import com.cleo.versalex.connector.Action;
import com.cleo.versalex.connector.Network;
import com.google.common.base.Strings;

public class LexFileFactory {
    IConnectorHost host = null;
    IConnectorAction action = null;
    Network network = null;
    String inbox = null;
    String source = null;
    String dest = null;
    int col = 0;
    Logger logger = null;

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

    public void setSourceAndDest(String source, String dest, int col, Logger logger) {
        this.source = source;
        this.dest = dest;
        this.col = col;
        this.logger = logger;
    }

    public LexFile getFile(Path path) {
        try {
            String filename = path.toString();
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

    public OutputStream getOutputStream(Path path, int col) throws IOException {
        LexFile file = getFile(path);
        try {
            NetworkFilterOutputStream nfos = new NetworkFilterOutputStream(LexIO.getFileOutputStream(file), (LexActionBean) action, false);
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
