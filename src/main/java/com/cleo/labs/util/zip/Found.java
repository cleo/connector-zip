package com.cleo.labs.util.zip;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Joiner;

public class Found implements Comparable<Found>, Cloneable {
    public enum Operation {add, delete, match};

    private static final String SLASH_CHAR = "/";
    private static final Joiner SLASH = Joiner.on(SLASH_CHAR);

    private String[] path;
    private Operation operation;
    private File file;
    private boolean directory;
    private long length;
    private long modified;
    private int depth;
    private int index;
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
    public int depth() {
        return depth;
    }
    public int index() {
        return index;
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
    public Found index(int index) {
        this.index = index;
        return this;
    }
    @JsonSetter
    public Found fullname(String fullname) {
        this.fullname = fullname;
        this.path = fullname.split(SLASH_CHAR);
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

    public Found() {
        this.operation = null;
        this.path = null;
        this.file = null;
        this.directory = false;
        this.length = -1;
        this.modified = -1;
        this.depth = -1;
        this.index = -1;
        this.fullname = null;
        this.contents = null;
        this.remote = false;
    }

    public Found(String[] path, File file, int depth, int index) {
        this.path = path;
        this.file = file;
        this.directory = file.isDirectory();
        this.length = file.length();
        this.modified = file.lastModified();
        this.depth = depth;
        this.index = index;
        this.fullname = SLASH.join(path);
        if (directory) {
            this.fullname += '/';
        }
        this.contents = null;
        this.remote = false;
    }

    public Found child(File child, int index) {
        String[] childpath = Arrays.copyOf(this.path, this.path.length+1);
        childpath[childpath.length-1] = child.getName();
        return new Found(childpath, child, this.depth+1, index);
    }
    public Found child(File child) {
        return child(child, 0);
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
        StringBuilder s = new StringBuilder();
        if (operation!=null) {
            s.append(operation).append(' ');
        }
        s.append(fullname).append(" [").append(depth).append('.').append(index).append("]");
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
    s.append(" path=null!");
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