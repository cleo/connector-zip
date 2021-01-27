package com.cleo.labs.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipFoundInputStream extends FoundInputStream {

    private ZipInputStream zip;

    public ZipFoundInputStream(InputStream in) {
        super(null);
        zip = new ZipInputStream(in);
        this.in = zip;
    }

    @Override
    public Found getNextEntry() throws IOException {
        Found found = null;
        ZipEntry entry = zip.getNextEntry();
        if (entry != null) {
            String entryPath = entry.getName();
            String[] safePath = PathUtil.safePath(entryPath);
            File entryFile = resolver.apply(safePath);
            found = new Found(safePath, entryFile, entry.isDirectory(), entry.getTime(), Found.UNKNOWN_LENGTH)
                    .operation(Found.Operation.add);
        }
        return found;
    }

    @Override
    public void closeEntry() throws IOException {
        zip.closeEntry();
    }

}
