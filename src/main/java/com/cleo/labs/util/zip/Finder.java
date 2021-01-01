package com.cleo.labs.util.zip;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Finder implements Iterator<Found>, Iterable<Found> {

    private enum State {
        /**
         * indicates an unstarted iterator where settings can still be changed.
         */
        NEW,
        /**
         * indicates an active iterator where the next element needs to be retrieved.
         */
        GET,
        /**
         * indicates an active iterator with the next element cached in {@code next}.
         */
        GOT,
        /**
         * indicates a completed iterator with no next element.
         */
        DONE}

    private State state;
    private Found next;
    private Found holdNext;
    private State holdState;
    private Deque<Found> holdDirectories;
    private Deque<Found> stack;
    private Deque<Found> pendingDirectories;
    private ArrayList<Found> checkpoint;
    private int count;

    private File root;
    private Predicate<Found> filter;
    public enum DirectoryMode {include, exclude, excludeEmpty, only};
    private DirectoryMode directoryMode;
    private int[] restart;
    private int limit;

    private void start() {
        Found start = new Found(new String[0], root, -1, 0);
        if (start.directory()) {
            stack.push(start);
        } else {
            stack.push(start);
        }
    }

    private Found advance() {
        Found result = null;
        while (result==null && !stack.isEmpty()) {
            // see if we need to report out a pendingDirectory
            if (!pendingDirectories.isEmpty()) {
                Found peek = stack.peek();
                // throw out already bypassed parents
                while (!pendingDirectories.isEmpty() && !pendingDirectories.peekLast().contains(peek)) {
                    // pendingDirectories.peekLast() doesn't enclose peek so remove it
                    pendingDirectories.removeLast();
                }
                if (!peek.directory() && !pendingDirectories.isEmpty()) {
                    // if we are peeking at a file, short-circuit the stack while we report out the parents
                    return pendingDirectories.pollFirst();
                }
            }
            // if not short-circuiting, now go back into the stack to pull the next Found
            result = stack.pop();
            push(result);
            if (result.directory()) {
                if (directoryMode == DirectoryMode.exclude) {
                    // don't report out this Found ever
                    result = null;
                } else if (directoryMode == DirectoryMode.excludeEmpty && !result.containsAFile()) {
                    // don't report out this Found right now
                    pendingDirectories.add(result);
                    result = null;
                }
            }
        }
        return result;
    }

    private void push(Found dir) {
        // keep track of the state stack, where to pick up next
        while (checkpoint.size() > dir.depth()+1) {
            checkpoint.remove(checkpoint.size()-1);
        }
        checkpoint.add(dir);
        // if it's a directory, push more onto the todo stack
        if (dir.directory() && filter.test(dir)) {
            File[] files = dir.file().listFiles();
            Found[] found = Stream.of(files)
                    .map(dir::child)
                    .filter(filter)
                    // sort: directories < files, otherwise compare fullname
                    .sorted((a,b) -> a.directory()==b.directory()
                        ? a.fullname().compareTo(b.fullname())
                        : a.directory() ? -1 : 1)
                    .map(new Function<Found,Found> () {
                        private int i = 0;
                        @Override
                        public Found apply(Found t) {
                            t.index(i++);
                            return t;
                        }
                    })
                    .toArray(Found[]::new);
            dir.contents(found);
            // if we are restarting, prune the list by the restart index
            // remember the list is still backwards, so we just adjust the stop index
            int start = 0;
            if (dir.depth() < restart.length-1) {
                start = restart[dir.depth()+1];
                restart[dir.depth()+1] = 0;
            }
            // now push them, in reverse to preserve order
            for (int i=found.length-1; i>=start; i--) {
                if (directoryMode != DirectoryMode.only || found[i].directory()) {
                    stack.push(found[i]);
                }
            }
        }
    }

    private boolean get() {
        if (state == State.NEW) {
            start();
            state = State.GET;
        }
        if (state == State.GET) { 
            if (limit > 0 && count >= limit) {
                next = null;
            } else {
                next = advance();
            }
            if (next == null) {
                state = State.DONE;
                checkpoint.clear();
            } else {
                state = State.GOT;
                count++;
            }
        }
        return state == State.GOT;
    }

    public Finder(File root) {
        this.root = root;
        this.filter = ALL;
        this.directoryMode = DirectoryMode.include;

        this.next = null;
        this.state = State.NEW;
        this.holdNext = null;
        this.holdState = State.NEW;
        this.holdDirectories = new ArrayDeque<>();
        this.stack = new ArrayDeque<>();
        this.pendingDirectories = new ArrayDeque<>();
        this.checkpoint = new ArrayList<>();
        this.restart = new int[0];
        this.limit = 0;
        this.count = 0;
    }

    public Finder filter(Predicate<Found> filter) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't set filter");
        }
        this.filter = filter;
        return this;
    }

    public Finder directoryMode(DirectoryMode directoryMode) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't set directoryMode");
        }
        this.directoryMode = directoryMode;
        return this;
    }

    public Finder restart(int[] restart) {
        if (state != State.NEW) {
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
        hasNext(); // want to hold in GOT state so checkpoint is where to resume
        this.limit = count;
        holdNext = next;
        holdState = state;
        if (directoryMode != DirectoryMode.exclude && !checkpoint.isEmpty()) {
            holdDirectories = new ArrayDeque<>();
            holdDirectories.addAll(checkpoint.subList(0, checkpoint.size()-1));
        }
        next = null;
        state = State.DONE;
    }

    public void unhold() {
        this.limit = 0;
        this.count = holdState==State.GOT ? 1 : 0;
        next = holdNext;
        state = holdState;
        if (directoryMode != DirectoryMode.exclude) {
            pendingDirectories = holdDirectories;
            holdDirectories = new ArrayDeque<>();
        }
        holdNext = null;
    }

    public int count() {
        return count - ((holdNext!=null ? holdState : state)==State.GOT ? 1 : 0); // subtract the lookahead if in GOT state
    }

    @Override
    public boolean hasNext() {
        return get();
    }

    @Override
    public Found next() {
        if (!get()) {
            throw new NoSuchElementException();
        }
        state = State.GET;
        return next;
    }

    public int[] checkpoint() {
        if (checkpoint.size() <= 1) {
            return new int[0];
        }
        int[] indices = new int[checkpoint.size()-1];
        for (int i=1; i<checkpoint.size(); i++) {
            indices[i-1] = checkpoint.get(i).index();
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
            .anyMatch(m -> m.matches(Paths.get("", f.path())));
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
        return f->f.directory() || Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path())));
    }

    public static Predicate<Found> only(String pattern) {
        if (pattern==null || pattern.isEmpty()) {
            return ALL;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
        return f->f.directory() || matcher.matches(Paths.get("", f.path()));
    }

    @Override
    public Iterator<Found> iterator() {
        return this;
    }

}
