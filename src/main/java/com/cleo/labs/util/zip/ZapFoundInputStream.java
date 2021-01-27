package com.cleo.labs.util.zip;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.cleo.labs.util.zip.Found.Operation;

public class ZapFoundInputStream extends FoundInputStream {

    private DataInputStream dis;
    private EmbeddedInputStream eis;
    private enum State {starting, reading, eof};
    private State state;

    public ZapFoundInputStream(InputStream in) {
        super(null);
        this.dis = new DataInputStream(in);
        this.in = dis;
        this.eis = null;
        this.state = State.starting;
    }

    @Override
    public Found getNextEntry() throws IOException {
        if (state==State.starting) {
            byte[] check = new byte[FoundInputStream.CLEO_SIGNATURE.length];
            dis.readFully(check);
            if (!Arrays.equals(check, CLEO_SIGNATURE)) {
                throw new IOException("not a Zap archive");
            }
            state = State.reading;
        } else if (state==State.eof) {
            return null;
        }
        closeEntry();
        try {
            Found found = Found.read(dis);
            String[] safePath = PathUtil.safePath(found.fullname());
            found.file(resolver.apply(safePath));
            if (found.operation()==Operation.add) {
                eis = new EmbeddedInputStream(dis); // only add has content
                in = eis;
            } else {
                in = new ByteArrayInputStream(new byte[0]);
                if (found.operation()==Operation.end) {
                    state = State.eof;
                    found = null; // null signals end in this interface contract
                }
            }
            return found;
        } catch (EOFException e) {
            state = State.eof;
            return null;
        }
    }

    @Override
    public void closeEntry() throws IOException {
        if (eis != null) {
            eis.close();
            eis = null;
            in = dis;
        }
    }

}
