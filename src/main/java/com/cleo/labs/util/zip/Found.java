package com.cleo.labs.util.zip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSetter;

public class Found implements Comparable<Found>, Cloneable {
    public enum Operation {
        add (0),
        delete (1),
        match (2),
        end(100);

        private int tag;
        private Operation(int tag) {
            this.tag = tag;
        }
        public int tag() {
            return tag;
        }
        public static Operation of(int tag) {
            for (Operation o : EnumSet.allOf(Operation.class)) {
                if (tag == o.tag) {
                    return o;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    public static final Found FOUND_END = new Found().operation(Operation.end);

    private String[] path;
    private Operation operation;
    private File file;
    private boolean directory;
    private long length;
    private long modified;
    private String fullname;
    private Found[] contents;
    private boolean remote;

    public String[] path() {
        return path;
    }
    @JsonGetter
    public Operation operation() {
        return operation;
    }
    public File file() {
        return file;
    }
    @JsonGetter
    public boolean directory() {
        return directory;
    }
    @JsonGetter
    public long length() {
        return length;
    }
    @JsonGetter
    public long modified() {
        return modified;
    }
    @JsonGetter
    public String fullname() {
        return this.fullname;
    }
    @JsonGetter
    @JsonInclude(Include.NON_NULL)
    public Found[] contents() {
        return contents;
    }
    public boolean remote() {
        return remote;
    }

    @JsonSetter
    public Found operation(Operation operation) {
        this.operation = operation;
        return this;
    }
    public Found file(File file) {
        this.file = file;
        return this;
    }
    @JsonSetter
    public Found directory(boolean directory) {
        this.directory = directory;
        return this;
    }
    @JsonSetter
    public Found length(long length) {
        this.length = length;
        return this;
    }
    @JsonSetter
    public Found modified(long modified) {
        this.modified = modified;
        return this;
    }
    @JsonSetter
    public Found fullname(String fullname) {
        this.fullname = fullname;
        this.path = PathUtil.split(fullname);
        return this;
    }
    @JsonSetter
    public Found contents(Found[] contents) {
        this.contents = contents;
        return this;
    }
    public Found remote(boolean remote) {
        this.remote = remote;
        return this;
    }

    /**
     * The default constructor is used by Jackson deserialization.
     */
    public Found() {
        this.operation = null;
        this.path = null;
        this.file = null;
        this.directory = false;
        this.length = UNKNOWN_LENGTH;
        this.modified = -1;
        this.fullname = null;
        this.contents = null;
        this.remote = false;
    }

    /**
     * The {@code File}-based constructor is used by the {@link Finder} to represent
     * files it has found.
     * @param path the path of the file found, relative to the Finder root
     * @param file the {@code File} that was found at the relative path
     */
    public Found(String[] path, File file) {
        this.path = path;
        this.file = file;
        this.directory = file.isDirectory();
        this.length = file.length();
        this.modified = file.lastModified();
        this.fullname = PathUtil.join(path);
        if (directory) {
            this.fullname += '/';
        }
        this.contents = null;
        this.remote = false;
    }

    public static final long UNKNOWN_LENGTH = -1L;

    /**
     * The attribute-based constructor is used by the {@link ZipDirectoryOutputStream} when
     * it finds entries in the archive. A {@code File} is computed by its resolver, but the
     * file may not exist yet so attributes like {@code directory} and {@code modified} are
     * explicitly supplied in the archive header. The {@code length} may not be known yet,
     * depending on the format.
     * @param path the cleaned up parsed path of the archive entry (see {@link PathUtil#safePath})
     * @param file the {@code File} calculated by the resolver (which probably does not exist)
     * @param directory {@code true} for directories, {@code false} for files
     * @param modified the modified time
     * @param length the length, or {@code UNKNOWN_LENGTH} if the length is not known
     */
    public Found(String[] path, File file, boolean directory, long modified, long length) {
        this();
        this.path = path;
        this.file = file;
        this.directory = directory;
        this.modified = modified;
        this.length = length;
        this.fullname = PathUtil.join(path);
        if (directory) {
            this.fullname += '/';
        }
    }

    /**
     * Reads and returns a new Found object from a DataInputStream in a way
     * that matches how it was written with {@link #write(DataOutputStream)}.
     * @param dis the DataInputStream
     * @return the new Found object
     * @throws IOException
     */
    public static Found read(DataInputStream dis) throws IOException {
        Found found = new Found();
        Operation op = Operation.of(dis.readUnsignedByte());
        if (op == Operation.end) {
            return FOUND_END;
        }
        found.operation(op);
        found.fullname(dis.readUTF());
        found.directory(dis.readBoolean());
        found.modified(dis.readLong());
        found.length(dis.readLong());
        return found;
    }

    /**
     * Writes the current object to a DataOutputStream in a way
     * that matches reading it back in with {@link #read(DataInputStream)}.
     * @param dos the DataOutputStream
     * @throws IOException
     */
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(operation().tag());
        if (operation() != Operation.end) { 
            dos.writeUTF(fullname());
            dos.writeBoolean(directory());
            dos.writeLong(modified());
            dos.writeLong(length());
        }
    }

    public Found child(File child) {
        String[] childpath = Arrays.copyOf(this.path, this.path.length+1);
        childpath[childpath.length-1] = child.getName();
        return new Found(childpath, child);
    }
    public boolean contains(Found found) {
        return directory &&
                (path.length==0 || found.fullname.startsWith(fullname));
    }
    public boolean containsAFile() {
        return contents!=null && contents.length>0 && !contents[contents.length-1].directory;
    }
    @Override
    public String toString() {
        if (operation==Operation.end) {
            return "FOUND END";
        }
        StringBuilder s = new StringBuilder();
        if (operation!=null) {
            s.append(operation).append(' ');
        }
        s.append(fullname);
        if (directory) {
            if (contents==null) {
                s.append(" contents=null");
            } else {
                s.append(" contents[").append(contents.length).append("]");
            }
        }
        if (!directory) {
            s.append(" length=").append(length).append(" modified=").append(modified);
        }
        if (path==null) {
            s.append(" path=null");
        }
        return s.toString();
    }
    @Override
    public int compareTo(Found f) {
        return directory==f.directory()
                ? fullname.compareTo(f.fullname())
                : directory ? -1 : 1;
    }
    @Override
    protected Found clone() throws CloneNotSupportedException {
        return (Found)super.clone();
    }

    private static final long WIGGLE = 2000; // 2-second modtime wiggle room: see https://en.wikipedia.org/wiki/ZIP_(file_format)

    public Found calculateReplica(Found remote) {
        if (!directory || !remote.directory) {
            throw new IllegalArgumentException("calculateReplica requires directories");
        }

        List<Found> operations = new ArrayList<>();
        int localindex = 0;
        int remoteindex = 0;
        while (localindex < contents.length || remoteindex < remote.contents.length) {
            if (remoteindex >= remote.contents.length ||
                    localindex < contents.length && contents[localindex].compareTo(remote.contents[remoteindex]) < 0) {
                operations.add(contents[localindex].operation(Operation.add));
                localindex++;
            } else if (localindex >= contents.length ||
                    remoteindex < remote.contents.length && contents[localindex].compareTo(remote.contents[remoteindex]) > 0) {
                operations.add(remote.contents[remoteindex].operation(Operation.delete));
                remoteindex++;
            } else if (contents[localindex].compareTo(remote.contents[remoteindex]) == 0 &&
                     !contents[localindex].directory &&
                     (contents[localindex].length != remote.contents[remoteindex].length ||
                      Math.abs(contents[localindex].modified - remote.contents[remoteindex].modified)>WIGGLE)) {
                operations.add(contents[localindex].operation(Operation.add));
                localindex++;
                remoteindex++;
            } else {
                operations.add(contents[localindex].operation(Operation.match));
                localindex++;
                remoteindex++;
            }
        }

        try {
            Found result = clone();
            result.contents = operations.toArray(new Found[operations.size()]);
            return result;
        } catch (CloneNotSupportedException impossible) {
            return null;
        }
    }
}