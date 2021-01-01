package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Predicate;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;

public class LocalFinderInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    private static final ObjectMapper mapper = new ObjectMapper();

    private Iterator<Found> directories;

    private OutputStream output;
    private LambdaWriterInputStream input;

    private LocalFinderInputStream(Finder finder) {
        super(null);
        this.directories = finder.directoryMode(DirectoryMode.only);
        this.input = new LambdaWriterInputStream(this);
        this.in = input;
        this.output = input.getOutputStream();
    }

    public static class Builder {
        private File path = null;
        private Predicate<Found> filter = Finder.ALL;

        public Builder(File path) {
            this.path = path;
        }

        public Builder filter(Predicate<Found> filter) {
            this.filter = filter;
            return this;
        }

        private static Finder setupFinder(File path, Predicate<Found> filter) {
            if (filter == null) {
                filter = Finder.ALL;
            }
            Finder finder = new Finder(path).filter(filter);
            return finder;
        }

        public LocalFinderInputStream build() {
            Finder finder = setupFinder(path, filter);
            return new LocalFinderInputStream(finder);
        }
    }

    public static Builder builder(File path) {
        return new Builder(path);
    }

    @Override
    public void write(OutputStream out) throws IOException {
        if (directories.hasNext()) {
            Found directory = directories.next();
            byte[] buffer = mapper.writeValueAsBytes(directory);
            output.write(Ints.toByteArray(buffer.length));
            output.write(buffer);
        } else {
            output.close();
        }
    }

}
