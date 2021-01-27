package com.cleo.labs.util.zip;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.cleo.labs.util.zip.Found.Operation;
import com.google.common.io.ByteStreams;

public class ZapFoundOutputStream extends FoundOutputStream {

    private DataOutputStream dos;
    private EmbeddedOutputStream eos;

    public ZapFoundOutputStream(OutputStream out) throws IOException {
        super(null);
        this.dos = new DataOutputStream(out);
        this.out = dos;
        this.eos = null;
        dos.write(FoundInputStream.CLEO_SIGNATURE);
    }

    @Override
    public void putNextEntry(Found found) throws IOException {
        closeEntry();
        found.write(dos);
        if (found.operation()==Operation.add) {
            eos = new EmbeddedOutputStream(dos); // only add has content
            out = eos;
        } else {
            out = ByteStreams.nullOutputStream();
        }
    }

    @Override
    public void closeEntry() throws IOException {
        if (eos != null) {
            eos.close();
            eos = null;
            out = ByteStreams.nullOutputStream();
        }
    }

    @Override
    public void close() throws IOException {
        putNextEntry(Found.FOUND_END);
        closeEntry();
        out = dos;
        super.close();
    }

}
