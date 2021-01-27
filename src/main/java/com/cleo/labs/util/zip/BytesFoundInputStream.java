package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.InputStream;

public class BytesFoundInputStream extends FoundInputStream {

    private String filename;

    public BytesFoundInputStream(InputStream in, String filename) {
        super(in);
        this.filename = filename;
    }

    @Override
    public Found getNextEntry() throws IOException {
        if (filename != null) {
            Found found = new Found()
                    .operation(Found.Operation.add)
                    .fullname(filename)
                    .modified(System.currentTimeMillis())
                    .length(Found.UNKNOWN_LENGTH)
                    .file(resolver.apply(new String[] {filename}));
            filename = null;
            return found;
        }
        return null;
    }

    @Override
    public void closeEntry() throws IOException {
    }

}
