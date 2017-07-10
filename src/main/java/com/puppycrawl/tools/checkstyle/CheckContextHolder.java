package com.puppycrawl.tools.checkstyle;

import java.util.SortedSet;
import java.util.TreeSet;

import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;

public class CheckContextHolder {
    private final ThreadLocal<Context> context = ThreadLocal.withInitial(Context::new);

    public FileContents getFileContents() {
        return getContext().fileContents;
    }

    public void setFileContents(FileContents fileContents) {
        getContext().fileContents = fileContents;
    }

    public SortedSet<LocalizedMessage> getMessages() {
        return getContext().messages;
    }

    public void clearMessages() {
        getContext().messages.clear();
    }

    private Context getContext() {
        return context.get();
    }

    private static class Context {
        /** The current file contents. */
        private FileContents fileContents;

        /** The sorted set for collecting messages. */
        private final SortedSet<LocalizedMessage> messages = new TreeSet<>();
    }
}
