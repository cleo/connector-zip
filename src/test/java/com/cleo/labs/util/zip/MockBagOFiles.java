package com.cleo.labs.util.zip;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cleo.labs.util.zip.ZipDirectoryInputStream.Opener;
import com.google.common.base.Functions;

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

        public String name(int index) {
            return String.format(path, index);
        }

        public String toString() {
            if (directory) {
                return String.format("%s[%d:%d] [%d]", path, start, count, contents.size());
            } else {
                return String.format("%s[%d:%d] '%c':%d", path, start, count, fill, size);
            }
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

    public long now() {
        return now;
    }

    public MockBagOFiles now(long now) {
        this.now = now;
        return this;
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

    public DirectoryVerifier verifier() {
        return new DirectoryVerifier(root, 0);
    }

    public static class FileVerifier {
        private Entry entry;
        private Set<String> names = null;

        public FileVerifier(Entry entry) {
            this.entry = entry;
        }

        public FillOutputStream verify(String name) {
            if (names == null) {
                names = IntStream.range(entry.start, entry.start+entry.count)
                        .mapToObj(i -> entry.name(i))
                        .collect(Collectors.toSet());
            }
            if (names.contains(name)) {
                names.remove(name);
                return new FillOutputStream(entry.fill, entry.size);
            }
            return null;
        }

        public boolean verified() {
            boolean verified = names != null && names.isEmpty();
            return verified;
        }
    }

    public static class DirectoryVerifier {
        private Entry entry;
        private String name;
        private List<FileVerifier> files = null;
        private Map<String,DirectoryVerifier> dirs = null; // dirs point to a Verifier, files to null (like a set)

        public DirectoryVerifier(Entry entry, int index) {
            this.entry = entry;
            this.name = entry.name(index);
            // if there are no files and/or no subdirs,
            // replace the nulls with empties so they will verify
            if (!entry.contents.stream().anyMatch(e -> !e.directory)) {
                files = Collections.emptyList();
            }
            if (!entry.contents.stream().anyMatch(e -> e.directory)) {
                dirs = Collections.emptyMap();
            }
        }

        private void setupFiles() {
            if (files == null) {
                files = entry.contents.stream()
                        .filter(e -> !e.directory)
                        .map(FileVerifier::new)
                        .collect(Collectors.toList());
            }
        }

        private void setupDirectories() {
            if (dirs == null) {
                dirs = entry.contents.stream()
                        .filter(e -> e.directory)
                        .flatMap(e -> (IntStream.range(e.start, e.start+e.count).mapToObj(i -> new DirectoryVerifier(e, i))))
                        .collect(Collectors.toMap(v -> v.name, Functions.identity()));
            }
        }

        private FillOutputStream file(String name) {
            setupFiles();
            for (FileVerifier file : files) {
                FillOutputStream result = file.verify(name);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        private DirectoryVerifier child(String name) {
            setupDirectories();
            if (dirs.containsKey(name)) {
                return dirs.get(name);
            }
            return null;
        }

        public FillOutputStream verify(Path path) {
            DirectoryVerifier dir = this;
            for (int i=0; i<path.getNameCount()-1; i++) {
                dir = dir.child(path.getName(i).toString());
                if (dir == null) {
                    return null;
                }
            }
            return dir.file(path.getFileName().toString());
        }

        public FillOutputStream verify(String path) {
            return verify(Paths.get(path));
        }

        public boolean filesVerified() {
            boolean verified = files != null && files.stream().allMatch(FileVerifier::verified);
            return verified;
        }

        public boolean verified() {
            boolean verified = filesVerified() && directoriesVerified();
            return verified;
        }

        public boolean directoriesVerified() {
            boolean verified = dirs != null && dirs.values().stream().allMatch(DirectoryVerifier::verified);
            return verified;
        }
    }

    @SuppressWarnings("serial")
    public class MockFile extends File {
        private Entry entry;
        
        public MockFile(Entry entry, int index) {
            super(entry.name(index));
            this.entry = entry;
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
                return entry.contents.stream()
                            .flatMap(e -> (IntStream.range(e.start, e.start+e.count).mapToObj(i -> new MockFile(e, i))))
                            .toArray(File[]::new);
            } else {
                return null;
            }
        }
        @Override
        public String[] list() {
            if (entry.directory) { 
                return entry.contents.stream()
                            .flatMap(e -> (IntStream.range(e.start, e.start+e.count).mapToObj(i -> e.name(i))))
                            .toArray(String[]::new);
            } else {
                return null;
            }
        }
    }

}
