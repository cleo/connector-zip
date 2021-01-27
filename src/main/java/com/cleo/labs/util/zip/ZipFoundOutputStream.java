package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;

public class ZipFoundOutputStream extends FoundOutputStream {

    private ZipOutputStream zip;
    private boolean skip = false;

    public ZipFoundOutputStream(OutputStream out) {
        super(null);
        zip = new ZipOutputStream(out);
        this.out = zip;
    }

    @Override
    public void putNextEntry(Found found) throws IOException {
        if (found.operation() != Found.Operation.add) { 
            skip = true;
            this.out = ByteStreams.nullOutputStream();
        } else {
            this.out = zip;
            skip = false;
            ZipEntry entry = new ZipEntry(found.fullname());
            entry.setTime(found.modified());
            if (found.directory()) {
                entry.setSize(0L);
            } else {
                entry.setSize(found.length());
                entry.setCompressedSize(found.length());
            }
            zip.putNextEntry(entry);
        }
    }

    @Override
    public void closeEntry() throws IOException {
        if (skip) {
            this.out = zip;
            skip = false;
        } else {
            zip.closeEntry();
        }
    }

    public ZipOutputStream zip() {
        return zip;
    }

}
