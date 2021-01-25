package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public interface UnzipProcessor {
    public OutputStream process(Found zip) throws IOException;

    public static UnzipProcessor defaultProcessor = zip -> {
        if (zip.directory()) {
            zip.file().mkdirs();
            return null;
        } else {
            File parent = zip.file().getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            } else if (!parent.isDirectory()) {
                throw new IOException("can not create parent directory for "+zip.fullname()+": file already exists");
            }
            return new FileOutputStream(zip.file());
        }
    };
}