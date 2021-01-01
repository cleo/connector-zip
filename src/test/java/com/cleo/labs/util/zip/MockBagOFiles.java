package com.cleo.labs.util.zip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.cleo.labs.util.zip.ZipDirectoryInputStream.Opener;

public class MockBagOFiles {

    private class Entry {
        public String path;
        public int start;
        public int count;
        public boolean directory;
        public int size;
        public byte fill;
        public List<Entry> contents;
        public Entry parent;

        public Entry(String path, int start, int count, int size, byte fill) {
            this.path = path;
            this.start = start;
            this.count = count;
            this.directory = false;
            this.size = size;
            this.fill = fill;
            this.parent = cursor;
            if (cursor != null) {
                cursor.contents.add(this);
            }
        }

        public Entry(String path, int start, int count) {
            this.path = path;
            this.start = start;
            this.count = count;
            this.directory = true;
            this.size = 0;
            this.contents = new ArrayList<>();
            this.parent = cursor;
            if (cursor != null) {
                cursor.contents.add(this);
            }
            cursor = this;
        }
    }

    private Entry cursor;
    private Entry root;
    private long now;

    public MockBagOFiles() {
        cursor = null;
        root = new Entry("/", 0, 1);
        now = System.currentTimeMillis();
    }

    public MockBagOFiles files(String path, int start, int count, int size, byte fill) {
        new Entry(path, start, count, size, fill);
        return this;
    }

    public MockBagOFiles dirs(String path, int start, int count) {
        new Entry(path, start, count);
        return this;
    }

    public MockBagOFiles up() {
        if (cursor.parent != null) {
            cursor = cursor.parent;
        }
        return this;
    }

    public Opener opener() {
        return f -> {
            MockFile mock = (MockFile)f.file();
            return new FillInputStream(mock.entry.fill, mock.entry.size);
        };
    }

    public MockFile root() {
        return new MockFile(root, 0);
    }

    @SuppressWarnings("serial")
    public class MockFile extends File {
        private Entry entry;
        private File[] contents;
        
        public MockFile(Entry entry, int index) {
            super(String.format(entry.path, index));
            this.entry = entry;
            this.contents = null;
        }
        @Override
        public boolean canExecute() {
            return false;
        }
        @Override
        public boolean canRead() {
            return true;
        }
        @Override
        public boolean canWrite() {
            return false;
        }
        @Override
        public boolean exists() {
            return true;
        }
        @Override
        public boolean isDirectory() {
            return entry.directory;
        }
        @Override
        public boolean isFile() {
            return !entry.directory;
        }
        @Override
        public boolean isHidden() {
            return false;
        }
        @Override
        public long lastModified() {
            return now;
        }
        @Override
        public long length() {
            return entry.size;
        }
        @Override
        public File[] listFiles() {
            if (entry.directory) {
                if (contents == null) {
                    contents = entry.contents.stream()
                            .flatMap(e -> (IntStream.range(e.start, e.start+e.count).mapToObj(i -> new MockFile(e, i))))
                            .toArray(File[]::new);
                }
                return contents;
            } else {
                return null;
            }
        }
        @Override
        public String[] list() {
            if (entry.directory) { 
                return Stream.of(listFiles()).map(File::getName).toArray(String[]::new);
            } else {
                return null;
            }
        }
    }

}
