package com.cleo.labs.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnzipDirectoryStreamWrapper implements AutoCloseable {

    private Function<String[],File> resolver;
    private Predicate<Found> filter;
    private UnzipProcessor processor;
    private BooleanSupplier interrupted;

    public UnzipDirectoryStreamWrapper(Function<String[],File> resolver) throws IOException {
        this.resolver = resolver;
        this.filter = Finder.ALL;
        this.processor = (zip)->null;
        this.interrupted = ()->false;
    }

    public UnzipDirectoryStreamWrapper filter(Predicate<Found> filter) {
        this.filter = filter==null ? Finder.ALL : filter;
        return this;
    }

    public UnzipDirectoryStreamWrapper processor(UnzipProcessor processor) {
        this.processor = processor==null ? (zip)->null : processor;
        return this;
    }

    public UnzipDirectoryStreamWrapper interrupted(BooleanSupplier interrupted) {
        this.interrupted = interrupted==null ? ()->false : interrupted;
        return this;
    }

    public static final int BUFFER_SIZE = 16384;

    public void process(ZipInputStream unzip) throws IOException {
        ZipEntry entry;
        byte[] buf = new byte[BUFFER_SIZE];
        while (!interrupted.getAsBoolean() && (entry = unzip.getNextEntry()) != null) {
            String entryPath = entry.getName();
            String[] safePath = PathUtil.safePath(entryPath);
            File entryFile = resolver.apply(safePath);
            Found found = new Found(safePath, entryFile, entry.isDirectory(), entry.getTime(), Found.UNKNOWN_LENGTH);
            if (filter.test(found)) {
                OutputStream out = processor.process(found);
                if (out!=null) {
                    try {
                        int n;
                        while (!interrupted.getAsBoolean() && (n = unzip.read(buf)) >= 0) {
                            out.write(buf, 0,  n);
                        }
                    } finally {
                        out.close();
                    }
                    if (entry.getTime() >= 0) {
                        try {
                            entryFile.setLastModified(entry.getTime());
                        } catch (Exception ignore) {
                            // don't worry about it -- some URIs don't allow this
                        }
                    }
                }
            }
            unzip.closeEntry();
        }
        unzip.close();
    }

    @Override
    public void close() {
    }

}
