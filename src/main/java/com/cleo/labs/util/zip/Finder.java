package com.cleo.labs.util.zip;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.base.Joiner;

public class Finder implements Iterator<Finder.Found>, Iterable<Finder.Found> {

    private static final Joiner SLASH = Joiner.on('/');

    public static class Found {
        public String[] path;
        public File file;
        public boolean directory;
        public int depth;
        public int index;
        public String fullname;
        public Found(String[] path, File file, boolean directory, int depth, int index) {
            this.path = path;
            this.file = file;
            this.directory = directory;
            this.depth = depth;
            this.index = index;
            this.fullname = SLASH.join(path);
            if (directory) {
                this.fullname += '/';
            }
        }
        public Found child(File child, int index) {
            String[] childpath = Arrays.copyOf(this.path, this.path.length+1);
            childpath[childpath.length-1] = child.getName();
            return new Found(childpath, child, child.isDirectory(), this.depth+1, index);
        }
        public Found child(File child) {
            return child(child, 0);
        }
        @Override
        public String toString() {
            return fullname+"(d="+depth+",i="+index+")";
        }
    }

    private boolean started;
    private Deque<Found> stack;
    private Found peeked;
    private Found hold;
    private Deque<Found> pendingDirectories;
    private ArrayList<Found> state;
    private int count;

    private File root;
    private Predicate<Found> filter;
    public enum DirectoryMode {include, exclude, excludeEmpty};
    private DirectoryMode directoryMode;
    private int[] restart;
    private int limit;

    private void push(Found dir) {
        // keep track of the state stack, where to pick up next
        if (dir.depth >= 0) {
            while (state.size() > dir.depth) {
                state.remove(state.size()-1);
            }
            state.add(dir);
        }
        // if it's a directory, push more onto the todo stack
        if (dir.directory && filter.test(dir)) {
            File[] files = dir.file.listFiles();
            Found[] found = Stream.of(files)
                    .map(dir::child)
                    .filter(filter)
                    // this is a reverse sort: files < directories, otherwise -compare
                    .sorted((a,b) -> a.directory==b.directory
                        ? -a.fullname.compareTo(b.fullname)
                        : a.directory ? 1 : -1)
                    .toArray(Found[]::new);
            // now push them, which reverses them back to directories < files
            int stop = found.length;
            if (dir.depth < restart.length-1) {
                stop -= restart[dir.depth+1];
                restart[dir.depth+1] = 0;
            }
            for (int i=0; i<stop; i++) {
                found[i].index = found.length-1-i;
                stack.push(found[i]);
            }
        }
    }

    private void peek() {
        Found next = null;
        while (next==null && !stack.isEmpty()) {
            // see if we need to report out a pendingDirectory
            if (!pendingDirectories.isEmpty()) {
                Found peek = stack.peek();
                // throw out already bypassed parents
                while (!pendingDirectories.isEmpty() &&
                    !peek.fullname.startsWith(pendingDirectories.peekLast().fullname)) {
                    // pendingDirectories.peekLast() doesn't enclose peek so remove it
                    pendingDirectories.removeLast();
                }
                if (!peek.directory && !pendingDirectories.isEmpty()) {
                    // if we are peeking at a file, short-circuit the stack while we report out the parents
                    peeked = pendingDirectories.pollFirst();
                    return;
                }
            }
            // now go back into the stack to pull the next Found
            next = stack.pop();
            push(next);
            if (next.directory && directoryMode != DirectoryMode.include) {
                // don't report out this Found, or at least not right now
                if (directoryMode == DirectoryMode.excludeEmpty) {
                    pendingDirectories.add(next);
                }
                next = null;
            }
        }
        if (next == null && stack.isEmpty()) {
            state.clear();
        }
        peeked = next;
    }

    public Finder(File root) {
        this.root = root;
        this.filter = ALL;
        this.directoryMode = DirectoryMode.include;

        this.stack = new ArrayDeque<>();
        this.hold = null;
        this.pendingDirectories = new ArrayDeque<>();
        this.state = new ArrayList<>();
        this.restart = new int[0];
        this.limit = 0;
        this.count = 0;
    }

    public Finder filter(Predicate<Found> filter) {
        if (started) {
            throw new IllegalStateException("Finder is iterating -- can't set filter");
        }
        this.filter = filter;
        return this;
    }

    public Finder directoryMode(DirectoryMode directoryMode) {
        if (started) {
            throw new IllegalStateException("Finder is iterating -- can't set directoryMode");
        }
        this.directoryMode = directoryMode;
        return this;
    }

    public Finder restart(int[] restart) {
        if (started) {
            throw new IllegalStateException("Finder is iterating -- can't set restart");
        }
        this.restart = restart==null ? new int[0] : restart.clone();
        return this;
    }

    public Finder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public void hold() {
        this.limit = count;
        hold = peeked;
        peeked = null;
    }

    public void unhold() {
        this.limit = 0;
        this.count = 0;
        peeked = hold;
        hold = null;
    }

    public int count() {
        return count;
    }

    private void start() {
        started = true;
        Found start = new Found(new String[0], root, root.isDirectory(), -1, 0);
        if (start.directory) {
            push(start);
        } else {
            stack.push(start);
        }
        peek();
    }

    @Override
    public boolean hasNext() {
        if (!started) {
            start();
        }
        return peeked != null;
    }

    @Override
    public Found next() {
        Found result = peeked;
        if (result != null) {
            count++;
            if (limit > 0 && count >= limit) {
                peeked = null;
            } else {
                peek();
            }
        }
        return result;
    }

    public int[] checkpoint() {
        int[] indices = new int[state.size()];
        for (int i=0; i<state.size(); i++) {
            indices[i] = state.get(i).index;
        }
        return indices;
    }

    public static Predicate<Found> ALL = found->true;

    public static Predicate<Found> NONE = found->false;

    public static Predicate<Found> excluding(String...patterns) {
        if (patterns==null || patterns.length==0) {
            return ALL;
        }
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->!Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path)));
    }

    public static Predicate<Found> including(String...patterns) {
        if (patterns==null || patterns.length==0) {
            return NONE;
        }
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->f.directory || Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path)));
    }

    public static Predicate<Found> only(String pattern) {
        if (pattern==null || pattern.isEmpty()) {
            return ALL;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
        return f->f.directory || matcher.matches(Paths.get("", f.path));
    }

    @Override
    public Iterator<Found> iterator() {
        return this;
    }

}
