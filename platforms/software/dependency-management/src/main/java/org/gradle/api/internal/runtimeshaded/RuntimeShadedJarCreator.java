/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.runtimeshaded;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.concurrent.MultiProducerSingleConsumerProcessor;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.PercentageProgressFormatter;
import org.gradle.model.internal.asm.AsmConstants;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Arrays.asList;

@NullMarked
class RuntimeShadedJarCreator {

    private static final int ADDITIONAL_PROGRESS_STEPS = 2;
    private static final String SERVICES_DIR_PREFIX = "META-INF/services/";
    private static final String CLASS_DESC = "Ljava/lang/Class;";

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeShadedJarCreator.class);

    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ImplementationDependencyRelocator remapper;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;

    public RuntimeShadedJarCreator(
        ProgressLoggerFactory progressLoggerFactory,
        BuildOperationExecutor buildOperationExecutor,
        ImplementationDependencyRelocator remapper,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder
    ) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.remapper = remapper;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
    }

    public void create(RuntimeShadedJarType type, final File outputJar, final Collection<? extends File> files) {
        LOGGER.info("Generating " + type.getDisplayName() + ": " + outputJar.getAbsolutePath());
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(RuntimeShadedJarCreator.class);
        progressLogger.setDescription("Generating " + type.getDisplayName());
        progressLogger.started();
        try {
            createFatJar(outputJar, files, progressLogger);
        } finally {
            progressLogger.completed();
        }
    }

    private void createFatJar(final File outputJar, final Collection<? extends File> files, final ProgressLogger progressLogger) {
        classpathBuilder.jar(outputJar, builder -> processFiles(builder, files, progressLogger));
    }

    private void processFiles(ClasspathBuilder.EntryBuilder builder, Collection<? extends File> files, ProgressLogger progressLogger) throws IOException {
        PercentageProgressFormatter progressFormatter = new PercentageProgressFormatter("Generating", Iterables.size(files) + ADDITIONAL_PROGRESS_STEPS);

        Map<String, List<String>> services = new LinkedHashMap<>();
        MultiProducerSingleConsumerProcessor<InputFile> writer = createShadedJarWriter(builder, progressLogger, progressFormatter, services);

        writer.start();
        try {
            buildOperationExecutor.runAll(queue -> {
                int index = 0;
                for (File file : files) {
                    InputFile inputFile = new InputFile(file, index++);
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) throws Exception {
                            classpathWalker.visit(inputFile.file, entry -> processEntry(inputFile, entry));
                            writer.submit(inputFile);
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("Visiting " + file.getName());
                        }
                    });
                }
            });
        } finally {
            writer.stop(Duration.ofMinutes(5));
        }

        writeServiceFiles(builder, services);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());

        writeIdentifyingMarkerFile(builder);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());
    }

    /**
     * The processed and remapped contents of a file that is to be included
     * in the relocated jar.
     */
    private static final class InputFile implements Comparable<InputFile> {

        private final int index;
        private final File file;
        private final List<String> names;
        private final List<byte[]> contents;
        private final Map<String, List<String>> services;

        public InputFile(File file, int index) {
            this.file = file;
            this.index = index;

            this.names = new ArrayList<>();
            this.contents = new ArrayList<>();
            this.services = new LinkedHashMap<>();
        }

        public void addServiceProviders(String serviceType, List<String> providers) {
            services.computeIfAbsent(serviceType, k -> new ArrayList<>()).addAll(providers);
        }

        public Map<String, List<String>> getServices() {
            return services;
        }

        /**
         * Put a new entry into the remapped result.
         */
        public void put(String name, byte[] content) {
            names.add(name);
            contents.add(content);
        }

        public void forEachEntry(EntryConsumer consumer) throws IOException {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                byte[] content = contents.get(i);
                consumer.accept(name, content);
            }
        }

        public int getIndex() {
            return index;
        }

        @Override
        public int compareTo(InputFile o) {
            return Integer.compare(index, o.index);
        }

        interface EntryConsumer {
            void accept(String name, byte[] content) throws IOException;
        }

    }

    private static MultiProducerSingleConsumerProcessor<InputFile> createShadedJarWriter(
        ClasspathBuilder.EntryBuilder builder,
        ProgressLogger progressLogger,
        PercentageProgressFormatter progressFormatter,
        Map<String, List<String>> services
    ) {
        return new MultiProducerSingleConsumerProcessor<>("shaded jar writer", new Consumer<InputFile>() {
            private int index = 0;
            private final PriorityQueue<InputFile> allProcessedFiles = new PriorityQueue<>();
            private final Set<String> seenPaths = new HashSet<>();

            @Override
            public void accept(InputFile processedFile) {
                allProcessedFiles.add(processedFile);

                InputFile toProcess;
                while (!allProcessedFiles.isEmpty() && (toProcess = allProcessedFiles.peek()).getIndex() == index) {
                    try {
                        progressLogger.progress(progressFormatter.getProgress());
                        toProcess.forEachEntry((name, content) -> {
                            if (seenPaths.add(name)) {
                                builder.put(name, content);
                            }
                        });
                        for (Map.Entry<String, List<String>> entry : toProcess.getServices().entrySet()) {
                            services.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                        }
                        progressFormatter.increment();
                        index++;
                        allProcessedFiles.poll();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write shaded jar", e);
                    }
                }
            }
        });
    }

    private void writeServiceFiles(ClasspathBuilder.EntryBuilder builder, Map<String, List<String>> services) throws IOException {
        for (Map.Entry<String, List<String>> service : services.entrySet()) {
            String allProviders = Joiner.on("\n").join(service.getValue());
            builder.put(SERVICES_DIR_PREFIX + service.getKey(), allProviders.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeIdentifyingMarkerFile(ClasspathBuilder.EntryBuilder builder) throws IOException {
        builder.put(GradleRuntimeShadedJarDetector.MARKER_FILENAME, new byte[0]);
    }

    private void processEntry(InputFile builder, ClasspathEntryVisitor.Entry entry) throws IOException {
        String name = entry.getName();
        if (name.equals("META-INF/MANIFEST.MF")) {
            return;
        }
        // Remove license files that cause collisions between a LICENSE file and a license/ directory.
        if (name.startsWith("LICENSE") || name.startsWith("license")) {
            return;
        }

        if (name.endsWith(".class")) {
            processClassFile(builder, entry);
        } else if (name.startsWith(SERVICES_DIR_PREFIX)) {
            processServiceDescriptor(builder, entry);
        } else {
            processResource(builder, entry);
        }
    }

    private static boolean isModuleInfoClass(String name) {
        return "module-info".equals(name);
    }

    private void processServiceDescriptor(InputFile inputFile, ClasspathEntryVisitor.Entry entry) throws IOException {
        String name = entry.getName();
        String descriptorName = name.substring(SERVICES_DIR_PREFIX.length());
        String descriptorApiClass = periodsToSlashes(descriptorName)[0];
        String relocatedApiClassName = remapper.maybeRelocateResource(descriptorApiClass);
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass;
        }

        byte[] bytes = entry.getContent();
        String content = new String(bytes, StandardCharsets.UTF_8).replaceAll("(?m)^#.*", "").trim(); // clean up comments and new lines

        String[] descriptorImplClasses = periodsToSlashes(separateLines(content));
        String[] relocatedImplClassNames = maybeRelocateResources(descriptorImplClasses);
        String serviceType = slashesToPeriods(relocatedApiClassName)[0];
        String[] serviceProviders = slashesToPeriods(relocatedImplClassNames);

        inputFile.addServiceProviders(serviceType, asList(serviceProviders));
    }

    private String[] slashesToPeriods(@Nullable String... slashClassNames) {
        return Arrays.stream(slashClassNames).filter(Objects::nonNull)
            .map(clsName -> clsName.replace('/', '.')).map(String::trim)
            .toArray(String[]::new);
    }

    private String[] periodsToSlashes(@Nullable String... periodClassNames) {
        return Arrays.stream(periodClassNames).filter(Objects::nonNull)
            .map(clsName -> clsName.replace('.', '/'))
            .toArray(String[]::new);
    }

    private void processResource(InputFile builder, ClasspathEntryVisitor.Entry entry) throws IOException {
        String name = entry.getName();
        byte[] resource = entry.getContent();

        int i = name.lastIndexOf("/");
        String path = i == -1 ? null : name.substring(0, i);

        if (remapper.keepOriginalResource(path)) {
            // we're writing 2 copies of the resource: one relocated, the other not, in order to support `getResource/getResourceAsStream` with
            // both absolute and relative paths
            builder.put(name, resource);
        }

        String remappedResourceName = path != null ? remapper.maybeRelocateResource(path) : null;
        if (remappedResourceName != null) {
            String newFileName = remappedResourceName + name.substring(i);
            builder.put(newFileName, resource);
        }
    }

    private void processClassFile(InputFile builder, ClasspathEntryVisitor.Entry entry) throws IOException {
        String name = entry.getName();
        String className = name.substring(0, name.length() - ".class".length());
        if (isModuleInfoClass(className)) {
            // do not include module-info files, as they would represent a bundled dependency module, instead of Gradle itself
            return;
        }
        byte[] bytes = entry.getContent();
        byte[] remappedClass = remapClass(className, bytes);

        String remappedClassName = remapper.maybeRelocateResource(className);
        String newFileName = (remappedClassName == null ? className : remappedClassName).concat(".class");

        builder.put(newFileName, remappedClass);
    }

    private byte[] remapClass(String className, byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor remappingVisitor = new ShadingClassRemapper(classWriter, remapper);

        try {
            classReader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new GradleException("Error in ASM processing class: " + className, e);
        }

        return classWriter.toByteArray();
    }

    private static class ShadingClassRemapper extends ClassRemapper {
        final Map<String, String> remappedClassLiterals;
        private final ImplementationDependencyRelocator dependencyRelocator;

        public ShadingClassRemapper(ClassWriter classWriter, ImplementationDependencyRelocator dependencyRelocator) {
            super(classWriter, dependencyRelocator);
            this.dependencyRelocator = dependencyRelocator;
            remappedClassLiterals = new HashMap<>();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            ImplementationDependencyRelocator.ClassLiteralRemapping remapping = null;
            if (CLASS_DESC.equals(desc)) {
                remapping = dependencyRelocator.maybeRemap(name);
                if (remapping != null) {
                    remappedClassLiterals.put(remapping.getLiteral(), remapping.getLiteralReplacement().replace("/", "."));
                }
            }
            return super.visitField(access, remapping != null ? remapping.getFieldNameReplacement() : name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String desc, String signature, String[] exceptions) {
            return new MethodVisitor(AsmConstants.ASM_LEVEL, super.visitMethod(access, name, desc, signature, exceptions)) {
                @Override
                public void visitLdcInsn(Object cst) {
                    if (cst instanceof String) {
                        String literal = remappedClassLiterals.get(cst);
                        if (literal == null) {
                            // tries to relocate literals in the form of foo/bar/Bar
                            literal = dependencyRelocator.maybeRelocateResource((String) cst);
                        }
                        if (literal == null) {
                            // tries to relocate literals in the form of foo.bar.Bar
                            literal = dependencyRelocator.maybeRelocateResource(((String) cst).replace('.', '/'));
                            if (literal != null) {
                                literal = literal.replace("/", ".");
                            }
                        }
                        super.visitLdcInsn(literal != null ? literal : cst);
                    } else {
                        super.visitLdcInsn(cst);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) && CLASS_DESC.equals(desc)) {
                        ImplementationDependencyRelocator.ClassLiteralRemapping remapping = dependencyRelocator.maybeRemap(name);
                        if (remapping != null) {
                            super.visitFieldInsn(opcode, owner, remapping.getFieldNameReplacement(), desc);
                            return;
                        }
                    }
                    super.visitFieldInsn(opcode, owner, name, desc);
                }
            };
        }
    }

    private String[] maybeRelocateResources(@Nullable String... resources) {
        return Arrays.stream(resources)
            .filter(Objects::nonNull)
            .map(resource -> {
                String remapped = remapper.maybeRelocateResource(resource);
                if (remapped == null) {
                    return resource; // This resource was not relocated. Use the original name.
                }
                return remapped;
            })
            .toArray(String[]::new);
    }

    private String[] separateLines(String entry) {
        return entry.split("\\n");
    }

}
