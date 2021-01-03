package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.OutputStream;

public class FillOutputStream extends OutputStream {

    private byte fill;
    private long limit;
    private long count;
    private boolean verified = false;
    private Runnable onVerify = null;

    public FillOutputStream(byte fill) {
        this(fill, -1L);
    }

    public FillOutputStream(byte fill, long limit) {
        this.fill = fill;
        this.limit = limit;
        this.count = 0L;
    }

    public static final String TOO_LONG = "attempt to write beyond limit";
    public static final String TOO_SHORT = "stream closed before limit reached";
    public static final String BAD_DATA = "attempt to write byte not matching fill";

    @Override
    public void write(int b) throws IOException {
        if (limit >= 0 && count+1 > limit) {
            throw new IOException(TOO_LONG+": "+(count+1)+" > "+limit);
        }
        if (b != (fill&0xff)) {
            throw new IOException(BAD_DATA);
        }
        count++;
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (limit >= 0) {
            if (count+len > limit) {
                throw new IOException(TOO_LONG+": "+(count+len)+" > "+limit);
            }
        }
        for (int i=0; i<len; i++) {
            if (b[off+i] != fill) {
                throw new IOException(BAD_DATA);
            }
        }
        count += len;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (limit >= 0 && count < limit) {
            throw new IOException(TOO_SHORT+": "+count+" < "+limit);
        }
        verified = true;
        if (onVerify != null) {
            onVerify.run();
        }
    }

    public FillOutputStream onVerify(Runnable onVerify) {
        this.onVerify = onVerify;
        return this;
    }

    public boolean verified() {
        return verified;
    }

}
