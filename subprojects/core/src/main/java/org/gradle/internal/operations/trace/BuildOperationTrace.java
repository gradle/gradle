/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations.trace;

import com.google.common.base.Charsets;
import com.google.common.base.StandardSystemProperty;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.util.GFileUtils;

import javax.annotation.Nonnull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Writes files describing the build operation stream for a build.
 * Can be enabled for any build with `-Dorg.gradle.internal.operations.trace=«path-base»`.
 *
 * Imposes no overhead when not enabled.
 * Also used as the basis for asserting on the event stream in integration tests, via BuildOperationFixture.
 *
 * Three files are created:
 *
 * - «path-base»-log.txt: a chronological log of events, each line is a JSON object
 * - «path-base»-tree.json: a JSON tree of the event structure
 * - «path-base»-tree.txt: A simplified tree representation showing basic information
 *
 * Generally, the simplified tree view is best for browsing.
 * The JSON tree view can be used for more detailed analysis — open in a JSON tree viewer, like Chrome.
 *
 * The «path-base» param is optional.
 * If invoked as `-Dorg.gradle.internal.operations.trace`, a base value of "operations" will be used.
 *
 * The “trace” produced here is different to the trace produced by Gradle Profiler.
 * There, the focus is analyzing the performance profile.
 * Here, the focus is debugging/developing the information structure of build operations.
 *
 * @since 4.0
 */
public class BuildOperationTrace implements Stoppable {

    public static final String SYSPROP = "org.gradle.internal.operations.trace";

    private static final byte[] NEWLINE = "\n".getBytes();
    private static final byte[] INDENT = "    ".getBytes();

    private final String basePath;
    private final OutputStream logOutputStream;

    private final BuildOperationListenerManager buildOperationListenerManager;
    private final ListenerManager listenerManager;

    private final BuildOperationListener listener = new LoggingListener();

    public BuildOperationTrace(StartParameter startParameter, BuildOperationListenerManager buildOperationListenerManager, ListenerManager listenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.listenerManager = listenerManager;

        Map<String, String> sysProps = startParameter.getSystemPropertiesArgs();
        String basePath = sysProps.get(SYSPROP);
        if (basePath == null) {
            basePath = System.getProperty(SYSPROP);
        }

        this.basePath = basePath;
        if (this.basePath == null || basePath.equals(Boolean.FALSE.toString())) {
            this.logOutputStream = null;
            return;
        }

        try {
            File logFile = logFile(basePath);
            GFileUtils.mkdirs(logFile.getParentFile());
            if (logFile.isFile()) {
                GFileUtils.forceDelete(logFile);
            }
            //noinspection ResultOfMethodCallIgnored
            logFile.createNewFile();

            this.logOutputStream = new BufferedOutputStream(new FileOutputStream(logFile));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        buildOperationListenerManager.addListener(listener);
        listenerManager.addListener(listener);
    }

    @Override
    public void stop() {
        buildOperationListenerManager.removeListener(listener);
        listenerManager.removeListener(listener);

        if (logOutputStream != null) {
            try {
                synchronized (logOutputStream) {
                    logOutputStream.close();
                }

                final List<BuildOperationRecord> roots = readLogToTreeRoots(logFile(basePath));
                writeDetailTree(roots);
                writeSummaryTree(roots);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void writeDetailTree(List<BuildOperationRecord> roots) throws IOException {
        try {
            String rawJson = JsonOutput.toJson(BuildOperationTree.serialize(roots));
            String prettyJson = JsonOutput.prettyPrint(rawJson);
            Files.asCharSink(file(basePath, "-tree.json"), Charsets.UTF_8).write(prettyJson);
        } catch (OutOfMemoryError e) {
            System.err.println("Failed to write build operation trace JSON due to out of memory.");
        }
    }

    private void writeSummaryTree(final List<BuildOperationRecord> roots) throws IOException {
        Files.asCharSink(file(basePath, "-tree.txt"), Charsets.UTF_8).writeLines(new Iterable<String>() {
            @Override
            @Nonnull
            public Iterator<String> iterator() {

                final Deque<Queue<BuildOperationRecord>> stack = new ArrayDeque<Queue<BuildOperationRecord>>(Collections.singleton(new ArrayDeque<BuildOperationRecord>(roots)));
                final StringBuilder stringBuilder = new StringBuilder();

                return new Iterator<String>() {
                    @Override
                    public boolean hasNext() {
                        if (stack.isEmpty()) {
                            return false;
                        } else if (stack.peek().isEmpty()) {
                            stack.pop();
                            return hasNext();
                        } else {
                            return true;
                        }
                    }

                    @Override
                    public String next() {
                        Queue<BuildOperationRecord> children = stack.peek();
                        BuildOperationRecord record = children.poll();

                        stringBuilder.setLength(0);

                        int indents = stack.size() - 1;

                        for (int i = 0; i < indents; ++i) {
                            stringBuilder.append("  ");
                        }

                        if (!record.children.isEmpty()) {
                            stack.addFirst(new ArrayDeque<BuildOperationRecord>(record.children));
                        }

                        stringBuilder.append(record.displayName);

                        if (record.details != null) {
                            stringBuilder.append(" ");
                            stringBuilder.append(JsonOutput.toJson(record.details));
                        }

                        if (record.result != null) {
                            stringBuilder.append(" ");
                            stringBuilder.append(JsonOutput.toJson(record.result));
                        }

                        stringBuilder.append(" [");
                        stringBuilder.append(record.endTime - record.startTime);
                        stringBuilder.append("ms]");

                        stringBuilder.append(" (");
                        stringBuilder.append(record.id);
                        stringBuilder.append(")");

                        if (!record.progress.isEmpty()) {
                            for (BuildOperationRecord.Progress progress : record.progress) {
                                stringBuilder.append(StandardSystemProperty.LINE_SEPARATOR.value());
                                for (int i = 0; i < indents; ++i) {
                                    stringBuilder.append("  ");
                                }
                                stringBuilder.append("- ")
                                    .append(progress.details).append(" [")
                                    .append(progress.time - record.startTime)
                                    .append("]");
                            }
                        }

                        return stringBuilder.toString();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        });
    }

    public static BuildOperationTree read(String basePath) {
        List<BuildOperationRecord> roots = readLogToTreeRoots(logFile(basePath));
        return new BuildOperationTree(roots);
    }

    private static List<BuildOperationRecord> readLogToTreeRoots(final File logFile) {
        try {
            final JsonSlurper slurper = new JsonSlurper();

            final List<BuildOperationRecord> roots = new ArrayList<BuildOperationRecord>();
            final Map<Object, PendingOperation> pendings = new HashMap<Object, PendingOperation>();
            final Map<Object, List<BuildOperationRecord>> childrens = new HashMap<Object, List<BuildOperationRecord>>();

            Files.asCharSource(logFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                @Override
                public boolean processLine(@SuppressWarnings("NullableProblems") String line) {
                    Map<String, ?> map = uncheckedCast(slurper.parseText(line));
                    if (map.containsKey("startTime")) {
                        SerializedOperationStart serialized = new SerializedOperationStart(map);
                        pendings.put(serialized.id, new PendingOperation(serialized));
                        childrens.put(serialized.id, new LinkedList<BuildOperationRecord>());
                    } else if (map.containsKey("time")) {
                        SerializedOperationProgress serialized = new SerializedOperationProgress(map);
                        PendingOperation pending = pendings.get(serialized.id);
                        assert pending != null : "did not find owner of progress event with ID " + serialized.id;
                        pending.progress.add(serialized);
                    } else {
                        SerializedOperationFinish finish = new SerializedOperationFinish(map);

                        PendingOperation pending = pendings.remove(finish.id);
                        assert pending != null;

                        List<BuildOperationRecord> children = childrens.remove(finish.id);
                        assert children != null;

                        SerializedOperationStart start = pending.start;

                        Map<String, ?> detailsMap = uncheckedCast(start.details);
                        Map<String, ?> resultMap = uncheckedCast(finish.result);

                        List<BuildOperationRecord.Progress> progresses = new ArrayList<BuildOperationRecord.Progress>();
                        for (SerializedOperationProgress progress : pending.progress) {
                            Map<String, ?> progressDetailsMap = uncheckedCast(progress.details);
                            progresses.add(new BuildOperationRecord.Progress(
                                progress.time,
                                progressDetailsMap,
                                progress.detailsClassName
                            ));
                        }

                        BuildOperationRecord record = new BuildOperationRecord(
                            start.id,
                            start.parentId,
                            start.displayName,
                            start.startTime,
                            finish.endTime,
                            detailsMap == null ? null : Collections.unmodifiableMap(detailsMap),
                            start.detailsClassName,
                            resultMap == null ? null : Collections.unmodifiableMap(resultMap),
                            finish.resultClassName,
                            finish.failureMsg,
                            progresses,
                            BuildOperationRecord.ORDERING.immutableSortedCopy(children)
                        );

                        if (start.parentId == null) {
                            roots.add(record);
                        } else {
                            List<BuildOperationRecord> parentChildren = childrens.get(start.parentId);
                            assert parentChildren != null : "parentChildren != null '" + line + "' from " + logFile;
                            parentChildren.add(record);
                        }
                    }

                    return true;
                }

                @Override
                public Void getResult() {
                    return null;
                }
            });

            assert pendings.isEmpty();

            return roots;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

    }

    private static File logFile(String basePath) {
        return file(basePath, "-log.txt");
    }

    private static File file(String base, String suffix) {
        return new File((base == null || base.trim().isEmpty() ? "operations" : base) + suffix).getAbsoluteFile();
    }

    static class PendingOperation {

        final SerializedOperationStart start;
        final List<SerializedOperationProgress> progress = new ArrayList<SerializedOperationProgress>();

        PendingOperation(SerializedOperationStart start) {
            this.start = start;
        }

    }


    private class LoggingListener extends InternalBuildAdapter implements BuildOperationListener {

        // This is a workaround for https://github.com/gradle/gradle/issues/4241
        // Several early typed operations have `buildPath` property,
        // the value of which can only be determined after the settings file for the build has loaded.
        //
        // The workaround is to buffer all operation notifications in memory until the root build's settings have loaded.
        // This works because all possible settings files have been evaluated by the time the root one has been.
        // This is not guaranteed to hold into the future.
        // A proper solution would be to change the operation details/results to be
        // truly immutable and convey values known at the time.
        private boolean buffering = true;
        private final Lock bufferLock = new ReentrantLock();
        private final Queue<Entry> buffer = new ConcurrentLinkedQueue<Entry>();

        @Override
        public void projectsLoaded(@SuppressWarnings("NullableProblems") Gradle gradle) {
            if (gradle.getParent() == null) {
                stopBuffering();
            }
        }

        // Build may have failed before getting to projectsLoaded
        @Override
        public void buildFinished(@SuppressWarnings("NullableProblems") BuildResult result) {
            stopBuffering();
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            if (buildOperation.getId().getId() == 11) {
                int i = 1;
            }
            new Entry(new SerializedOperationStart(buildOperation, startEvent), false).add();
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
            new Entry(new SerializedOperationProgress(buildOperationId, progressEvent), false).add();
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            new Entry(new SerializedOperationFinish(buildOperation, finishEvent), false).add();
        }

        private void stopBuffering() {
            if (buffering) {
                bufferLock.lock();
                try {
                    if (buffering) {
                        for (Entry entry : buffer) {
                            entry.write();
                        }
                        buffer.clear();
                        buffering = false;
                    }
                } finally {
                    bufferLock.unlock();
                }
            }
        }

        private final class Entry {
            final SerializedOperation operation;
            final boolean indent;

            Entry(SerializedOperation operation, boolean indent) {
                this.operation = operation;
                this.indent = indent;
            }

            public void add() {
                if (buffering) {
                    bufferLock.lock();
                    try {
                        if (buffering) {
                            if (operation instanceof SerializedOperationStart) {
                                if (((SerializedOperationStart) operation).id == 11) {
                                    int i = 1;
                                }
                            }
                            buffer.add(this);
                        } else {
                            write();
                        }
                    } finally {
                        bufferLock.unlock();
                    }
                } else {
                    write();
                }
            }

            @SuppressWarnings("ConstantConditions")
            private void write() {
                String json = JsonOutput.toJson(operation.toMap());
                try {
                    synchronized (logOutputStream) {
                        if (indent) {
                            logOutputStream.write(INDENT);
                        }
                        logOutputStream.write(json.getBytes("UTF-8"));
                        logOutputStream.write(NEWLINE);
                        logOutputStream.flush();
                    }
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

        }

    }

    static Object toSerializableModel(Object object) {
        if (object instanceof CustomOperationTraceSerialization) {
            return ((CustomOperationTraceSerialization) object).getCustomOperationTraceSerializableModel();
        } else {
            return object;
        }
    }
}
