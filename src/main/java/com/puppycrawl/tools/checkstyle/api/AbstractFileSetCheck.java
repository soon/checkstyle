////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2017 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import com.puppycrawl.tools.checkstyle.utils.CommonUtils;

/**
 * Provides common functionality for many FileSetChecks.
 *
 * @author lkuehne
 * @author oliver
 */
public abstract class AbstractFileSetCheck
    extends AbstractViolationReporter
    implements FileSetCheck {

    /** Collects the error messages. */
    private final LocalizedMessages messageCollector = new LocalizedMessages();

    /** The dispatcher errors are fired to. */
    private MessageDispatcher messageDispatcher;

    /** The file extensions that are accepted by this filter. */
    private String[] fileExtensions = CommonUtils.EMPTY_STRING_ARRAY;



    protected final Stats stats = new Stats();

    protected FileStats fileStats;

    /**
     * Called to process a file that matches the specified file extensions.
     * @param file the file to be processed
     * @param lines an immutable list of the contents of the file.
     * @throws CheckstyleException if error condition within Checkstyle occurs.
     */
    protected abstract void processFiltered(File file, List<String> lines)
            throws CheckstyleException;

    @Override
    public void init() {
        // No code by default, should be overridden only by demand at subclasses
    }

    @Override
    public void destroy() {
        // No code by default, should be overridden only by demand at subclasses
    }

    @Override
    public void beginProcessing(String charset) {
        // No code by default, should be overridden only by demand at subclasses
    }

    @Override
    public final SortedSet<LocalizedMessage> process(File file, List<String> lines)
            throws CheckstyleException {
        messageCollector.reset();
        // Process only what interested in
        if (CommonUtils.matchesFileExtension(file, fileExtensions)) {
            final String fileName = file.getPath();
            fileStats = new FileStats().setFileName(fileName)
                .setFileSize(file.length());

            final long beforeTotal = System.nanoTime();

            processFiltered(file, lines);

            final long afterTotal = System.nanoTime();
            fileStats.setTotalTime(afterTotal - beforeTotal);
            stats.addFileStats(fileStats);
        }
        return messageCollector.getMessages();
    }

    @Override
    public void finishProcessing() {
        try {
            writeStatsToFile();
        }
        catch (FileNotFoundException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Unable to write stats", ex);
        }
    }

    public void writeStatsToFile() throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter writer = new PrintWriter(
            "stats-" + getClass().getSimpleName() + ".csv", "UTF-8")) {
            writeRowToFile(writer, "File",
                "ast", "walk", "append comments", "walk comments", "total", "file size (KB)");
            for (FileStats fileStats : stats.getFileStats()) {
                writeRowToFile(writer,
                    fileStats.getFileName(),
                    fileStats.getParseAstTime(),
                    fileStats.getWalkOrdinaryTime(),
                    fileStats.getAppendHiddenCommentNotesTime(),
                    fileStats.getWalkWithCommentsTime(),
                    fileStats.getTotalTime(),
                    fileStats.getFileSize());
            }
            writeRowToFile(writer);
        }
    }

    public void writeRowToFile(PrintWriter writer, Object... values) {
        final StringBuilder builder = new StringBuilder();
        String separator = "";
        for (Object value : values) {
            builder.append(separator).append(value);
            separator = ",";
        }
        builder.append('\n');
        writer.write(builder.toString());
    }

    @Override
    public final void setMessageDispatcher(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }

    /**
     * A message dispatcher is used to fire violation messages to
     * interested audit listeners.
     *
     * @return the current MessageDispatcher.
     */
    protected final MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }

    /**
     * @return file extensions that identify the files that pass the
     *     filter of this FileSetCheck.
     */
    public String[] getFileExtensions() {
        return Arrays.copyOf(fileExtensions, fileExtensions.length);
    }

    /**
     * Sets the file extensions that identify the files that pass the
     * filter of this FileSetCheck.
     * @param extensions the set of file extensions. A missing
     *         initial '.' character of an extension is automatically added.
     * @throws IllegalArgumentException is argument is null
     */
    public final void setFileExtensions(String... extensions) {
        if (extensions == null) {
            throw new IllegalArgumentException("Extensions array can not be null");
        }

        fileExtensions = new String[extensions.length];
        for (int i = 0; i < extensions.length; i++) {
            final String extension = extensions[i];
            if (CommonUtils.startsWithChar(extension, '.')) {
                fileExtensions[i] = extension;
            }
            else {
                fileExtensions[i] = "." + extension;
            }
        }
    }

    /**
     * Returns the collector for violation messages.
     * Subclasses can use the collector to find out the violation
     * messages to fire via the message dispatcher.
     *
     * @return the collector for localized messages.
     */
    protected final LocalizedMessages getMessageCollector() {
        return messageCollector;
    }

    @Override
    public final void log(int line, String key, Object... args) {
        log(line, 0, key, args);
    }

    @Override
    public final void log(int lineNo, int colNo, String key,
            Object... args) {
        messageCollector.add(
                new LocalizedMessage(lineNo,
                        colNo,
                        getMessageBundle(),
                        key,
                        args,
                        getSeverityLevel(),
                        getId(),
                        getClass(),
                        getCustomMessages().get(key)));
    }

    /**
     * Notify all listeners about the errors in a file.
     * Calls {@code MessageDispatcher.fireErrors()} with
     * all logged errors and than clears errors' list.
     * @param fileName the audited file
     */
    protected final void fireErrors(String fileName) {
        final SortedSet<LocalizedMessage> errors = messageCollector
                .getMessages();
        messageCollector.reset();
        getMessageDispatcher().fireErrors(fileName, errors);
    }

    public static class Stats {
        private final List<FileStats> fileStats = new ArrayList<>();

        public void addFileStats(FileStats fileStat) {
            fileStats.add(fileStat);
        }

        public List<FileStats> getFileStats() {
            return Collections.unmodifiableList(fileStats);
        }
    }

    public static class FileStats {
        private String fileName;
        private long parseAstTime;
        private long walkOrdinaryTime;
        private long appendHiddenCommentNotesTime;
        private long walkWithCommentsTime;
        private long totalTime;
        private long fileSize;

        public String getFileName() {
            return fileName;
        }

        public FileStats setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public long getParseAstTime() {
            return parseAstTime;
        }

        public FileStats setParseAstTime(long parseAstTime) {
            this.parseAstTime = parseAstTime;
            return this;
        }

        public long getWalkOrdinaryTime() {
            return walkOrdinaryTime;
        }

        public FileStats setWalkOrdinaryTime(long walkOrdinaryTime) {
            this.walkOrdinaryTime = walkOrdinaryTime;
            return this;
        }

        public long getAppendHiddenCommentNotesTime() {
            return appendHiddenCommentNotesTime;
        }

        public FileStats setAppendHiddenCommentNotesTime(long appendHiddenCommentNotesTime) {
            this.appendHiddenCommentNotesTime = appendHiddenCommentNotesTime;
            return this;
        }

        public long getWalkWithCommentsTime() {
            return walkWithCommentsTime;
        }

        public FileStats setWalkWithCommentsTime(long walkWithCommentsTime) {
            this.walkWithCommentsTime = walkWithCommentsTime;
            return this;
        }

        public long getTotalTime() {
            return totalTime;
        }

        public FileStats setTotalTime(long totalTime) {
            this.totalTime = totalTime;
            return this;
        }

        public long getFileSize() {
            return fileSize;
        }

        public FileStats setFileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }
    }
}
