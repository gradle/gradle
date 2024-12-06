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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalFlag;
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

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.function.Consumer;
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
 * The “trace” produced here is different to the trace produced by Gradle Profiler.
 * There, the focus is analyzing the performance profile.
 * Here, the focus is debugging/developing the information structure of build operations.
 *
 * @since 4.0
 */
@ServiceScope(Scope.CrossBuildSession.class)
public class BuildOperationTrace implements Stoppable {

    public static final String SYSPROP = "org.gradle.internal.operations.trace";

    private static final StringInternalOption TRACE_OPTION = new StringInternalOption(SYSPROP, null);

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

    private static final StringInternalOption FILTER_OPTION = new StringInternalOption(FILTER_SYSPROP, null);

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
    private final BuildOperationListener listener;
    private final String basePath;

    private final OutputStream logOutputStream;
    private final ObjectMapper objectMapper = createObjectMapper();
    private final BuildOperationListenerManager buildOperationListenerManager;

    public BuildOperationTrace(StartParameter startParameter, BuildOperationListenerManager buildOperationListenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;

        InternalOptions internalOptions = new DefaultInternalOptions(startParameter.getSystemPropertiesArgs());
        this.basePath = internalOptions.getOption(TRACE_OPTION).get();
        if (this.basePath == null || basePath.equals(Boolean.FALSE.toString())) {
            this.logOutputStream = null;
            this.outputTree = false;
            this.listener = null;
            return;
        }

        Set<String> filter = getFilter(internalOptions);
        if (filter != null) {
            this.outputTree = false;
            this.listener = new FilteringBuildOperationListener(new SerializingBuildOperationListener(this::write), filter);
        } else {
            this.outputTree = internalOptions.getOption(TRACE_TREE_OPTION).get();
            this.listener = new SerializingBuildOperationListener(this::write);
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
        buildOperationListenerManager.removeListener(listener);
        if (logOutputStream != null) {
            try {
                synchronized (logOutputStream) {
                    logOutputStream.close();
                }

                if (outputTree) {
                    List<BuildOperationRecord> roots = readLogToTreeRoots(logFile(basePath), false);
                    writeDetailTree(roots);
                    writeSummaryTree(roots);
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void write(SerializedOperation operation) {
        try {
            String json = objectMapper.writeValueAsString(operation.toMap());
            synchronized (logOutputStream) {
                logOutputStream.write(json.getBytes(StandardCharsets.UTF_8));
                logOutputStream.write(NEWLINE);
                logOutputStream.flush();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeDetailTree(List<BuildOperationRecord> roots) throws IOException {
        File outputFile = file(basePath, "-tree.json");
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(outputFile, BuildOperationTree.serialize(roots));
    }

    private void writeSummaryTree(final List<BuildOperationRecord> roots) throws IOException {
        Path outputPath = Paths.get(basePath + "-tree.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
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
                        throw UncheckedException.throwAsUncheckedException(e);
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
                            convertProgressEvents(pending.progress),
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
                    0, 0, null, null, null, null, null,
                    convertProgressEvents(danglingProgress),
                    Collections.emptyList()
                ));
            }

            return roots;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

    }

    private static List<BuildOperationRecord.Progress> convertProgressEvents(List<SerializedOperationProgress> toConvert) {
        List<BuildOperationRecord.Progress> progresses = new ArrayList<>();
        for (SerializedOperationProgress progress : toConvert) {
            Map<String, ?> progressDetailsMap = uncheckedCast(progress.details);
            progresses.add(new BuildOperationRecord.Progress(
                progress.time,
                progressDetailsMap,
                progress.detailsClassName
            ));
        }
        return progresses;
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
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
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

        private final Consumer<SerializedOperation> consumer;

        public SerializingBuildOperationListener(Consumer<SerializedOperation> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            consumer.accept(new SerializedOperationStart(buildOperation, startEvent));
        }

        @Override
        public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
            consumer.accept(new SerializedOperationProgress(buildOperationId, progressEvent));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            consumer.accept(new SerializedOperationFinish(buildOperation, finishEvent));
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
}
