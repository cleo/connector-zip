package com.cleo.labs.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cleo.connector.shell.interfaces.IConnectorAction;
import com.cleo.connector.shell.interfaces.IConnectorHost;
import com.cleo.labs.connector.zip.FileFactory;
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

        public String path(MockFile parent, int index) {
            String parentName = parent==null ? "" : parent.getPath();
            if (parentName.equals("/")) {
                parentName = "";
            }
            return PathUtil.append(parentName, name(index));
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
        root = new Entry("", 0, 1);
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

    public FileFactory factory() {
        return new FileFactory() {
            @Override
            public void setup(IConnectorHost host, IConnectorAction action) {
            }
            @Override
            public void setSourceAndDest(String source, String dest, int col, Consumer<String> debug) {
            }
            @Override
            public File getFile(String filename) {
                // returns the root File
                return root();
            }
            @Override
            public InputStream getInputStream(File file) throws IOException {
                MockFile mock = (MockFile)file;
                return new FillInputStream(mock.entry.fill, mock.entry.size);
            }
            @Override
            public OutputStream getOutputStream(File file, long modtime) throws IOException {
                throw new IOException("not supported");
            }
        };
    }

    public MockFile root() {
        return new MockFile(null, root, 0);
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

        synchronized public FillOutputStream verify(String name) {
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

        @Override
        public String toString() {
            return toString("");
        }
        public String toString(String indent) {
            return indent+"fileVerifier("+entry+"): "+names;
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

        synchronized private void setupFiles() {
            if (files == null) {
                files = entry.contents.stream()
                        .filter(e -> !e.directory)
                        .map(FileVerifier::new)
                        .collect(Collectors.toList());
            }
        }

        synchronized private void setupDirectories() {
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
            return dirs.get(name);
        }

        public FillOutputStream verify(String[] path) {
            DirectoryVerifier dir = this;
            for (int i=0; i<path.length-1; i++) {
                dir = dir.child(path[i]);
                if (dir == null) {
                    return null;
                }
            }
            return dir.file(path[path.length-1]);
        }

        public FillOutputStream verify(String path) {
            return verify(PathUtil.safePath(path));
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

        public FileFactory factory() {
            return new FileFactory() {
                @Override
                public void setup(IConnectorHost host, IConnectorAction action) {
                }
                @Override
                public void setSourceAndDest(String source, String dest, int col, Consumer<String> debug) {
                }
                @Override
                public File getFile(String filename) {
                    return new File(filename);
                }

                @Override
                public InputStream getInputStream(File file) throws IOException {
                    throw new IOException("not supported");
                }
                @Override
                public OutputStream getOutputStream(File file, long modtime) throws IOException {
                    OutputStream out = verify(file.getPath());
                    if (out==null) {
                        throw new IOException("path not found or duplicate: "+file.getPath());
                    }
                    return out;
                }
            };
        }
        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String indent) {
            StringBuilder s = new StringBuilder();
            s.append(indent)
             .append("directoryVerifier(").append(name).append(": ").append(entry).append(")");
            if (files!=null) {
                for (FileVerifier file : files) {
                    s.append("\n").append(file.toString(indent+"  "));
                }
            }
            if (dirs!=null) {
                for (DirectoryVerifier dir : dirs.values()) {
                    s.append("\n").append(dir.toString(indent+"  "));
                }
            }
            return s.toString();
        }
    }

    @SuppressWarnings("serial")
    public class MockFile extends File {
        private Entry entry;
        private int index;
        private long modtime;
        private MockFile parent;
        
        public MockFile(MockFile parent, Entry entry, int index) {
            super(entry.path(parent, index));
            this.entry = entry;
            this.index = index;
            this.modtime = now;
            this.parent = parent;
        }
        public MockFile child(Entry entry, int index) {
            MockFile child = new MockFile(this, entry, index);
            return child;
        }
        @Override
        public String getName() {
            return entry.name(index);
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
            return modtime;
        }
        @Override
        public long length() {
            return entry.size;
        }
        @Override
        public File[] listFiles() {
            if (entry.directory) {
                return entry.contents.stream()
                            .flatMap(e -> (IntStream.range(e.start, e.start+e.count).mapToObj(i -> child(e, i))))
                            .toArray(MockFile[]::new);
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
        @Override
        public String getParent() {
            return getParentFile().getPath();
        }
        @Override
        public File getParentFile() {
            return parent;
        }
        @Override
        public boolean setLastModified(long time) {
            this.modtime = time;
            return true;
        }
        @Override
        public boolean mkdir() {
            return true; // yeah sure
        }
        @Override
        public boolean mkdirs() {
            return true; // yeah sure
        }
    }

}
