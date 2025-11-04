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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import org.gradle.StartParameter;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalFlag;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.buildoption.StringInternalOption;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Writes files describing the build operation stream for a build.
 * Can be enabled for any build with {@code -Dorg.gradle.internal.operations.trace=«path-base»}.
 * <p>
 * Imposes no overhead when not enabled.
 * Also used as the basis for asserting on the event stream in integration tests, via BuildOperationFixture.
 * <p>
 * Three files are created:
 * <ul>
 * <li>«path-base»-log.txt: a chronological log of events, each line is a JSON object</li>
 * <li>«path-base»-tree.json: a JSON tree of the event structure</li>
 * <li>«path-base»-tree.txt: A simplified tree representation showing basic information</li>
 * </ul>
 * <p>
 * Generally, the simplified tree view is best for browsing.
 * The JSON tree view can be used for more detailed analysis — open in a JSON tree viewer, like Chrome.
 * <p>
 * The «path-base» param is optional.
 * If invoked as {@code -Dorg.gradle.internal.operations.trace}, a base value of "operations" will be used.
 * <p>
 * The generation of trees can be very memory hungry and thus can be disabled with
 * {@code -Dorg.gradle.internal.operations.trace.tree=false}.
 * </p>
 * The "trace" produced here is different to the trace produced by Gradle Profiler.
 * There, the focus is analyzing the performance profile.
 * Here, the focus is debugging/developing the information structure of build operations.
 *
 * @since 4.0
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class BuildOperationTrace implements Stoppable {

    public static final String SYSPROP = "org.gradle.internal.operations.trace";

    private static final InternalOption<@Nullable String> TRACE_OPTION = StringInternalOption.of(SYSPROP);

    /**
     * A list of either details or result class names, delimited by {@link #FILTER_SEPARATOR},
     * that will be captured by this trace. When enabled, only operations matching this filter
     * will be captured. This enables capturing a build operation traces of larger builds that
     * would otherwise be too large to capture.
     *
     * When this property is set, a complete build operation tree is not captured. In this
     * case, only the log file will be written, not the formatted tree output files.
     */
    public static final String FILTER_SYSPROP = SYSPROP + ".filter";

    private static final InternalOption<@Nullable String> FILTER_OPTION = StringInternalOption.of(FILTER_SYSPROP);

    /**
     * A flag controlling whether tree generation is enabled ({@code true} by default).
     * Only application when {@link #FILTER_SYSPROP} is not set.
     */
    public static final String TREE_SYSPROP = SYSPROP + ".tree";

    private static final InternalFlag TRACE_TREE_OPTION = new InternalFlag(TREE_SYSPROP, true);

    /**
     * Delimiter for entries in {@link #FILTER_SYSPROP}.
     */
    public static final String FILTER_SEPARATOR = ";";

    private static final byte[] NEWLINE = {(byte) '\n'};

    private final boolean outputTree;
    private final @Nullable BuildOperationListener listener;
    private final @Nullable TraceWriter writer;

    private final BuildOperationListenerManager buildOperationListenerManager;

    public BuildOperationTrace(StartParameter startParameter, BuildOperationListenerManager buildOperationListenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;

        InternalOptions internalOptions = new DefaultInternalOptions(startParameter.getSystemPropertiesArgs());
        String basePath = internalOptions.getOption(TRACE_OPTION).get();
        if (basePath == null || basePath.equals("false")) {
            this.outputTree = false;
            this.listener = null;
            this.writer = null;
            return;
        }

        this.writer = new AsyncTraceWriter(new DefaultTraceWriter(basePath));
        Set<String> filter = getFilter(internalOptions);
        if (filter != null) {
            this.outputTree = false;
            this.listener = new FilteringBuildOperationListener(new SerializingBuildOperationListener(writer), filter);
        } else {
            this.outputTree = internalOptions.getOption(TRACE_TREE_OPTION).get();
            this.listener = new SerializingBuildOperationListener(writer);
        }

        buildOperationListenerManager.addListener(listener);
    }

    @Nullable
    private static Set<String> getFilter(InternalOptions internalOptions) {
        String filterProperty = internalOptions.getOption(FILTER_OPTION).get();
        if (filterProperty == null) {
            return null;
        }

        return new HashSet<>(Arrays.asList(filterProperty.split(FILTER_SEPARATOR)));
    }

    @Override
    public void stop() {
        if (listener != null) {
            buildOperationListenerManager.removeListener(listener);
        }
        if (writer != null) {
            writer.complete(outputTree);
        }
    }

    private static class DefaultTraceWriter implements TraceWriter {

        private final String basePath;
        private final ObjectMapper objectMapper;
        private final OutputStream logOutputStream;

        public DefaultTraceWriter(String basePath) {
            this.basePath = basePath;
            this.objectMapper = createObjectMapper();
            this.logOutputStream = openStream(logFile(basePath));
        }

        private static ObjectMapper createObjectMapper() {
            return new ObjectMapper()
                .registerModule(new SimpleModule()
                    .addSerializer(Class.class, new JsonClassSerializer())
                    .addSerializer(Throwable.class, new JsonThrowableSerializer())
                    .addSerializer(AttributeContainer.class, new JsonAttributeContainerSerializer())
                    .setSerializerModifier(new SkipDeprecatedBeanSerializerModifier())
                )
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        }

        private static OutputStream openStream(File logFile) {
            try {
                GFileUtils.mkdirs(logFile.getParentFile());
                if (logFile.isFile()) {
                    GFileUtils.forceDelete(logFile);
                }
                //noinspection ResultOfMethodCallIgnored
                logFile.createNewFile();
                return new BufferedOutputStream(Files.newOutputStream(logFile.toPath()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void write(SerializedOperation serializedOperation) {
            try {
                objectMapper.writeValue(logOutputStream, serializedOperation.toMap());
                logOutputStream.write(NEWLINE);
                logOutputStream.flush();
            } catch (IOException e) {
                IoActions.closeQuietly(logOutputStream);
                throw new UncheckedIOException(e);
            } catch (Throwable t) {
                IoActions.closeQuietly(logOutputStream);
                throw t;
            }
        }

        @Override
        public void complete(boolean writeTree) {
            try {
                if (writeTree) {
                    doWriteTreeJson();
                }
            } finally {
                IoActions.closeQuietly(logOutputStream);
            }
        }

        private void doWriteTreeJson() {
            try {
                List<BuildOperationRecord> roots = readLogToTreeRoots(logFile(basePath), false);
                writeDetailTree(roots);
                writeSummaryTree(roots);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeDetailTree(List<BuildOperationRecord> roots) throws IOException {
            File outputFile = file(basePath, "-tree.json");

            System.out.println("Writing build operation tree to " + outputFile.getAbsoluteFile().toPath());
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputFile, BuildOperationTree.serialize(roots));
            System.out.println("Finished writing build operation tree");
        }

        private void writeSummaryTree(final List<BuildOperationRecord> roots) throws IOException {
            Path outputPath = Paths.get(basePath + "-tree.txt");
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                doWriteSummaryTree(roots, writer);
            }
        }

        private void doWriteSummaryTree(List<BuildOperationRecord> roots, BufferedWriter writer) throws IOException {
            Deque<Queue<BuildOperationRecord>> stack = new ArrayDeque<>(Collections.singleton(new ArrayDeque<>(roots)));
            StringBuilder stringBuilder = new StringBuilder();

            while (!stack.isEmpty()) {
                if (stack.peek().isEmpty()) {
                    stack.pop();
                    continue;
                }

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
                    try {
                        stringBuilder.append(objectMapper.writeValueAsString(record.details));
                    } catch (JsonProcessingException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

                if (record.result != null) {
                    stringBuilder.append(" ");
                    try {
                        stringBuilder.append(objectMapper.writeValueAsString(record.result));
                    } catch (JsonProcessingException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

                stringBuilder.append(" [");
                stringBuilder.append(record.endTime - record.startTime);
                stringBuilder.append("ms]");

                stringBuilder.append(" (");
                stringBuilder.append(record.id);
                stringBuilder.append(")");

                if (!record.progress.isEmpty()) {
                    for (BuildOperationRecord.Progress progress : record.progress) {
                        stringBuilder.append(System.lineSeparator());
                        for (int i = 0; i < indents; ++i) {
                            stringBuilder.append("  ");
                        }
                        stringBuilder.append("- ")
                            .append(progress.details).append(" [")
                            .append(progress.time - record.startTime)
                            .append("]");
                    }
                }

                writer.write(stringBuilder.toString());
                writer.newLine();
            }
        }

    }

    public static BuildOperationTree read(String basePath) {
        File logFile = logFile(basePath);
        List<BuildOperationRecord> roots = readLogToTreeRoots(logFile, true);
        return new BuildOperationTree(roots);
    }

    /**
     * Reads a list of records that represent a partial build operation tree.
     * Some operations may not contain all of their children.
     * Some operations' parents may be missing from the tree.
     * Operations with missing parents are placed at the root of the returned tree.
     *
     * @param basePath The same path used for {@link #SYSPROP} when the trace was recorded.
     */
    public static BuildOperationTree readPartialTree(String basePath) {
        File logFile = logFile(basePath);
        List<BuildOperationRecord> partialTree = readLogToTreeRoots(logFile, false);
        return new BuildOperationTree(partialTree);
    }

    private static List<BuildOperationRecord> readLogToTreeRoots(final File logFile, boolean completeTree) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();

            final List<BuildOperationRecord> roots = new ArrayList<>();
            final Map<Object, PendingOperation> pendings = new HashMap<>();
            final Map<Object, List<BuildOperationRecord>> childrens = new HashMap<>();

            final List<SerializedOperationProgress> danglingProgress = new ArrayList<>();

            try (Stream<String> lines = Files.lines(logFile.toPath())) {
                lines.forEach(line -> {
                    Map<String, ?> map;
                    try {
                        map = objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Error reading line: '" + line + "'", e);
                    }

                    if (map.containsKey("startTime")) {
                        SerializedOperationStart serialized = new SerializedOperationStart(map);
                        pendings.put(serialized.id, new PendingOperation(serialized));
                        childrens.put(serialized.id, new LinkedList<>());
                    } else if (map.containsKey("time")) {
                        SerializedOperationProgress serialized = new SerializedOperationProgress(map);
                        PendingOperation pending = pendings.get(serialized.id);
                        if (pending != null) {
                            pending.progress.add(serialized);
                        } else {
                            if (completeTree) {
                                throw new IllegalStateException("did not find owner of progress event with ID " + serialized.id);
                            }

                            danglingProgress.add(serialized);
                        }
                    } else {
                        SerializedOperationFinish finish = new SerializedOperationFinish(map);

                        PendingOperation pending = pendings.remove(finish.id);
                        assert pending != null;

                        List<BuildOperationRecord> children = childrens.remove(finish.id);
                        assert children != null;

                        SerializedOperationStart start = pending.start;

                        Map<String, ?> detailsMap = uncheckedCast(start.details);
                        Map<String, ?> resultMap = uncheckedCast(finish.result);

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
                            pending.progress,
                            BuildOperationRecord.ORDERING.immutableSortedCopy(children)
                        );

                        if (start.parentId == null) {
                            roots.add(record);
                        } else {
                            List<BuildOperationRecord> parentChildren = childrens.get(start.parentId);
                            if (parentChildren != null) {
                                parentChildren.add(record);
                            } else {
                                if (completeTree) {
                                    throw new IllegalStateException("parentChildren != null '" + line + "' from " + logFile);
                                }

                                // We are not expecting a complete tree, so it is possible that the parent
                                // was never serialized. In that case, just treat this record as a root.
                                roots.add(record);
                            }
                        }
                    }
                });
            }

            assert pendings.isEmpty();

            if (!completeTree && !danglingProgress.isEmpty()) {
                // There were dangling progress events that have parent operations which were not serialized.
                // Add a dummy root operation to hold these events.
                roots.add(new BuildOperationRecord(
                    -1L, null,
                    "Dangling pending operations",
                    0L, 0L, null, null, null, null, null,
                    danglingProgress,
                    Collections.emptyList()
                ));
            }

            return roots;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

    }

    private static File logFile(String basePath) {
        return file(basePath, "-log.txt");
    }

    private static File file(@Nullable String base, String suffix) {
        return new File((base == null || base.trim().isEmpty() ? "operations" : base) + suffix).getAbsoluteFile();
    }

    static class PendingOperation {

        final SerializedOperationStart start;

        final List<SerializedOperationProgress> progress = new ArrayList<>();

        PendingOperation(SerializedOperationStart start) {
            this.start = start;
        }

    }

    public static @Nullable Object toSerializableModel(@Nullable Object object) {
        if (object instanceof CustomOperationTraceSerialization) {
            return ((CustomOperationTraceSerialization) object).getCustomOperationTraceSerializableModel();
        } else {
            return object;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class JsonClassSerializer extends JsonSerializer<Class> {
        @Override
        public void serialize(Class aClass, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(aClass.getName());
        }
    }

    private static class JsonThrowableSerializer extends JsonSerializer<Throwable> {
        @Override
        public void serialize(Throwable throwable, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            String message = throwable.getMessage();
            if (message != null) {
                gen.writeStringField("message", message);
            }
            gen.writeStringField("stackTrace", Throwables.getStackTraceAsString(throwable));
            gen.writeEndObject();
        }
    }

    /**
     * A custom serializer is needed to deal with the fact that our {@link AttributeContainer} implementations
     * have {@link AttributeContainer#getAttributes()} methods that return {@code this}.
     *
     * Attempting to serialize any of these causes a stack overflow, so
     * just convert them to an easily serializable {@link Map} first.
     */
    private static class JsonAttributeContainerSerializer extends JsonSerializer<AttributeContainer> {
        @Override
        public void serialize(AttributeContainer attributeContainer, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            /*
             * We need to convert to a map manually since asMap is only available on the internal container type,
             * which even though we know should always be a safe cast, it isn't a type that is available in this project.
             */
            ImmutableMap.Builder<Attribute<?>, ?> builder = ImmutableMap.builder();
            for (Attribute<?> attribute : attributeContainer.keySet()) {
                builder.put(attribute, Cast.uncheckedCast(Objects.requireNonNull(attributeContainer.getAttribute(attribute))));
            }
            serializers.defaultSerializeValue(builder.build(), gen);
        }
    }

    /**
     * Avoid serializing any deprecated properties, since they either trigger deprecation warnings
     * unnecessarily, or they might trigger some workaround behavior we otherwise want to avoid.
     */
    private static class SkipDeprecatedBeanSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties
        ) {
            // Remove any property where the member (field or getter) is annotated with @Deprecated
            beanProperties.removeIf(writer -> {
                AnnotatedMember member = writer.getMember();
                return member != null && member.hasAnnotation(Deprecated.class);
            });

            return beanProperties;
        }
    }

    private static class SerializingBuildOperationListener implements BuildOperationListener {

        private final TraceWriter writer;

        public SerializingBuildOperationListener(TraceWriter writer) {
            this.writer = writer;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            writer.write(new SerializedOperationStart(buildOperation, startEvent));
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
            writer.write(new SerializedOperationProgress(buildOperationId, progressEvent));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            writer.write(new SerializedOperationFinish(buildOperation, finishEvent));
        }
    }

    private static class FilteringBuildOperationListener implements BuildOperationListener {

        private final BuildOperationListener delegate;
        private final Set<String> filter;

        public FilteringBuildOperationListener(BuildOperationListener delegate, Set<String> filter) {
            this.delegate = delegate;
            this.filter = filter;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            if (buildOperation.getDetails() != null && filter.contains(buildOperation.getDetails().getClass().getName())) {
                delegate.started(buildOperation, startEvent);
            }
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
            if (progressEvent.getDetails() != null && filter.contains(progressEvent.getDetails().getClass().getName())) {
                delegate.progress(operationIdentifier, progressEvent);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            if ((buildOperation.getDetails() != null && filter.contains(buildOperation.getDetails().getClass().getName())) ||
                (finishEvent.getResult() != null && filter.contains(finishEvent.getResult().getClass().getName()))
            ) {
                delegate.finished(buildOperation, finishEvent);
            }
        }
    }

    interface TraceWriter {

        /**
         * Write a serialized operation to the log file.
         */
        void write(SerializedOperation serializedOperation);

        /**
         * This method must be called after all write operations have been completed.
         */
        void complete(boolean writeTree);

    }

    /**
     * A {@link TraceWriter} that offloads all writing operations to a separate thread.
     */
    private static class AsyncTraceWriter implements TraceWriter {

        private final TraceWriter delegate;
        private final AsyncExecutor executor;

        public AsyncTraceWriter(TraceWriter delegate) {
            this.delegate = delegate;
            this.executor = new AsyncExecutor();
        }

        @Override
        public void write(SerializedOperation operation) {
            executor.execute(() -> delegate.write(operation));
        }

        @Override
        public void complete(boolean outputTree) {
            try {
                executor.execute(() -> delegate.complete(outputTree));
            } finally {
                IoActions.closeQuietly(executor);
            }
        }

    }

    /**
     * Executes submitted operations sequentially in a separate thread.
     * <p>
     * This executor takes special care to ensure that any exceptions thrown by
     * submitted actions are rethrown on the calling thread, rather than being
     * silently ignored.
     * <p>
     * The use case for this executor strongly overlaps with that of
     * {@link org.gradle.kotlin.dsl.concurrent.AsyncIOScopeFactory}.
     * We should consider merging these implementations.
     */
    private static class AsyncExecutor implements Closeable {

        private final ExecutorService executor;
        private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();

        public AsyncExecutor() {
            this.executor = Executors.newSingleThreadExecutor();
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private void execute(Runnable r) {
            // Throw any exception that was caught in a previous operation
            checkForException();

            // Enqueue this operation
            try {
                executor.submit(() -> {
                    try {
                        r.run();
                    } catch (Throwable e) {
                        if (failure.compareAndSet(null, e)) {
                            // This is the first failure. Cancel all other operations
                            executor.shutdownNow();
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                // Executor has been shut down. Rethrow the original failure if present
                checkForException();

                // Executor was shut down, but we didn't do it. Just rethrow the rejected exception.
                throw e;
            }
        }

        @Override
        public void close() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    throw new RuntimeException("Timed out waiting for trace writer to complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw UncheckedException.throwAsUncheckedException(e);
            }
            checkForException();
        }

        private void checkForException() {
            Throwable failure = this.failure.get();
            if (failure != null) {
                throw new RuntimeException("Failure when writing build operation trace", failure);
            }
        }
    }

}
