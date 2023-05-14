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
import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.gradle.StartParameter;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nonnull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

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

    private final String basePath;
    private final OutputStream logOutputStream;
    private final JsonGenerator jsonGenerator = createJsonGenerator();

    private final BuildOperationListenerManager buildOperationListenerManager;

    private final BuildOperationListener listener = new BuildOperationListener() {
        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            write(new SerializedOperationStart(buildOperation, startEvent));
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
            write(new SerializedOperationProgress(buildOperationId, progressEvent));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            write(new SerializedOperationFinish(buildOperation, finishEvent));
        }
    };

    public BuildOperationTrace(StartParameter startParameter, BuildOperationListenerManager buildOperationListenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;

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
    }

    @Override
    public void stop() {
        buildOperationListenerManager.removeListener(listener);
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

    private void write(SerializedOperation operation) {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(JsonOutput.class.getClassLoader());
        try {
            String json = jsonGenerator.toJson(operation.toMap());
            try {
                synchronized (logOutputStream) {
                    logOutputStream.write(json.getBytes(StandardCharsets.UTF_8));
                    logOutputStream.write(NEWLINE);
                    logOutputStream.flush();
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    private void writeDetailTree(List<BuildOperationRecord> roots) throws IOException {
        try {
            String rawJson = jsonGenerator.toJson(BuildOperationTree.serialize(roots));
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

                final Deque<Queue<BuildOperationRecord>> stack = new ArrayDeque<>(Collections.singleton(new ArrayDeque<>(roots)));
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
                        Queue<BuildOperationRecord> children = stack.element();
                        BuildOperationRecord record = children.remove();

                        stringBuilder.setLength(0);

                        int indents = stack.size() - 1;

                        for (int i = 0; i < indents; ++i) {
                            stringBuilder.append("  ");
                        }

                        if (!record.children.isEmpty()) {
                            stack.addFirst(new ArrayDeque<>(record.children));
                        }

                        stringBuilder.append(record.displayName);

                        if (record.details != null) {
                            stringBuilder.append(" ");
                            stringBuilder.append(jsonGenerator.toJson(record.details));
                        }

                        if (record.result != null) {
                            stringBuilder.append(" ");
                            stringBuilder.append(jsonGenerator.toJson(record.result));
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
        File logFile = logFile(basePath);
        List<BuildOperationRecord> roots = readLogToTreeRoots(logFile);
        return new BuildOperationTree(roots);
    }

    private static List<BuildOperationRecord> readLogToTreeRoots(final File logFile) {
        try {
            final JsonSlurper slurper = new JsonSlurper();

            final List<BuildOperationRecord> roots = new ArrayList<>();
            final Map<Object, PendingOperation> pendings = new HashMap<>();
            final Map<Object, List<BuildOperationRecord>> childrens = new HashMap<>();

            Files.asCharSource(logFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                @Override
                public boolean processLine(@SuppressWarnings("NullableProblems") String line) {
                    Map<String, ?> map = uncheckedNonnullCast(slurper.parseText(line));
                    if (map.containsKey("startTime")) {
                        SerializedOperationStart serialized = new SerializedOperationStart(map);
                        pendings.put(serialized.id, new PendingOperation(serialized));
                        childrens.put(serialized.id, new LinkedList<>());
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

                        List<BuildOperationRecord.Progress> progresses = new ArrayList<>();
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

        final List<SerializedOperationProgress> progress = new ArrayList<>();

        PendingOperation(SerializedOperationStart start) {
            this.start = start;
        }

    }

    public static Object toSerializableModel(Object object) {
        if (object instanceof CustomOperationTraceSerialization) {
            return ((CustomOperationTraceSerialization) object).getCustomOperationTraceSerializableModel();
        } else {
            return object;
        }
    }

    private static JsonGenerator createJsonGenerator() {
        return new JsonGenerator.Options()
            .addConverter(new JsonGenerator.Converter() {
                @Override
                public boolean handles(Class<?> type) {
                    return Class.class.equals(type);
                }

                @Override
                public Object convert(Object value, String key) {
                    Class<?> clazz = (Class<?>) value;
                    return clazz.getName();
                }
            })
            .build();
    }
}
