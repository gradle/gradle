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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.progress.PercentageProgressFormatter;
import org.gradle.util.GFileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.Arrays.asList;

class RuntimeShadedJarCreator {

    private static final int ADDITIONAL_PROGRESS_STEPS = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeShadedJarCreator.class);

    private static final int BUFFER_SIZE = 8192;
    private static final String SERVICES_DIR_PREFIX = "META-INF/services/";
    private static final String CLASS_DESC = "Ljava/lang/Class;";

    private final ProgressLoggerFactory progressLoggerFactory;
    private final ImplementationDependencyRelocator remapper;
    private final ClasspathWalker classpathWalker;

    public RuntimeShadedJarCreator(ProgressLoggerFactory progressLoggerFactory, ImplementationDependencyRelocator remapper, ClasspathWalker classpathWalker) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.remapper = remapper;
        this.classpathWalker = classpathWalker;
    }

    public void create(final File outputJar, final Iterable<? extends File> files) {
        LOGGER.info("Generating " + outputJar.getAbsolutePath());
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(RuntimeShadedJarCreator.class);
        progressLogger.setDescription("Generating " + outputJar.getName());
        progressLogger.started();
        try {
            createFatJar(outputJar, files, progressLogger);
        } finally {
            progressLogger.completed();
        }
    }

    private void createFatJar(final File outputJar, final Iterable<? extends File> files, final ProgressLogger progressLogger) {
        final File tmpFile = tempFileFor(outputJar);

        IoActions.withResource(openJarOutputStream(tmpFile), new ErroringAction<ZipOutputStream>() {
            @Override
            protected void doExecute(ZipOutputStream jarOutputStream) throws Exception {
                processFiles(jarOutputStream, files, progressLogger);
                jarOutputStream.finish();
            }
        });

        GFileUtils.moveFile(tmpFile, outputJar);
    }

    private File tempFileFor(File outputJar) {
        try {
            final File tmpFile = File.createTempFile(outputJar.getName(), ".tmp");
            tmpFile.deleteOnExit();
            return tmpFile;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private ZipOutputStream openJarOutputStream(File outputJar) {
        try {
            ZipOutputStream outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar), BUFFER_SIZE));
            outputStream.setLevel(0);
            return outputStream;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void processFiles(ZipOutputStream outputStream, Iterable<? extends File> files, ProgressLogger progressLogger) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        Set<String> seenPaths = new HashSet<>();
        Map<String, List<String>> services = new LinkedHashMap<>();

        PercentageProgressFormatter progressFormatter = new PercentageProgressFormatter("Generating", Iterables.size(files) + ADDITIONAL_PROGRESS_STEPS);
        for (File file : files) {
            progressLogger.progress(progressFormatter.getProgress());
            classpathWalker.visit(file, entry -> processEntry(outputStream, entry, buffer, seenPaths, services));

            progressFormatter.increment();
        }

        writeServiceFiles(outputStream, services);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());

        writeIdentifyingMarkerFile(outputStream);
        progressLogger.progress(progressFormatter.incrementAndGetProgress());
    }

    private void writeServiceFiles(ZipOutputStream outputStream, Map<String, List<String>> services) throws IOException {
        for (Map.Entry<String, List<String>> service : services.entrySet()) {
            String allProviders = Joiner.on("\n").join(service.getValue());
            writeEntry(outputStream, SERVICES_DIR_PREFIX + service.getKey(), allProviders.getBytes(Charsets.UTF_8));
        }
    }

    private void writeIdentifyingMarkerFile(ZipOutputStream outputStream) throws IOException {
        writeEntry(outputStream, GradleRuntimeShadedJarDetector.MARKER_FILENAME, new byte[0]);
    }

    private void processEntry(ZipOutputStream outputStream, ClasspathEntryVisitor.Entry entry, byte[] buffer, final Set<String> seenPaths, Map<String, List<String>> services) throws IOException {
        String name = entry.getName();
        if (name.equals("META-INF/MANIFEST.MF")) {
            return;
        }
        // Remove license files that cause collisions between a LICENSE file and a license/ directory.
        if (name.startsWith("LICENSE") || name.startsWith("license")) {
            return;
        }
        if (!name.startsWith(SERVICES_DIR_PREFIX) && !seenPaths.add(name)) {
            return;
        }

        if (name.endsWith(".class")) {
            processClassFile(outputStream, entry);
        } else if (name.startsWith(SERVICES_DIR_PREFIX)) {
            processServiceDescriptor(entry, services);
        } else {
            copyEntry(outputStream, entry, buffer);
        }
    }

    private static boolean isModuleInfoClass(String name) {
        return "module-info".equals(name);
    }

    private void processServiceDescriptor(ClasspathEntryVisitor.Entry entry, Map<String, List<String>> services) throws IOException {
        String name = entry.getName();
        String descriptorName = name.substring(SERVICES_DIR_PREFIX.length());
        String descriptorApiClass = periodsToSlashes(descriptorName)[0];
        String relocatedApiClassName = remapper.maybeRelocateResource(descriptorApiClass);
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass;
        }

        byte[] bytes = entry.getContent();
        String content = new String(bytes, Charsets.UTF_8).replaceAll("(?m)^#.*", "").trim(); // clean up comments and new lines

        String[] descriptorImplClasses = periodsToSlashes(separateLines(content));
        String[] relocatedImplClassNames = maybeRelocateResources(descriptorImplClasses);
        if (relocatedImplClassNames.length == 0) {
            relocatedImplClassNames = descriptorImplClasses;
        }

        String serviceType = slashesToPeriods(relocatedApiClassName)[0];
        String[] serviceProviders = slashesToPeriods(relocatedImplClassNames);

        if (!services.containsKey(serviceType)) {
            services.put(serviceType, Lists.newArrayList(serviceProviders));
        } else {
            List<String> providers = services.get(serviceType);
            providers.addAll(asList(serviceProviders));
        }
    }

    private String[] slashesToPeriods(String... slashClassNames) {
        return asList(slashClassNames).stream().filter(Objects::nonNull)
            .map(clsName -> clsName.replace('/', '.')).map(String::trim)
            .toArray(String[]::new);
    }

    private String[] periodsToSlashes(String... periodClassNames) {
        return asList(periodClassNames).stream().filter(Objects::nonNull)
            .map(clsName -> clsName.replace('.', '/'))
            .toArray(String[]::new);
    }

    private void copyEntry(ZipOutputStream outputStream, ClasspathEntryVisitor.Entry entry, byte[] buffer) throws IOException {
        String name = entry.getName();
        byte[] resource = entry.getContent();

        int i = name.lastIndexOf("/");
        String path = i == -1 ? null : name.substring(0, i);

        if (remapper.keepOriginalResource(path)) {
            // we're writing 2 copies of the resource: one relocated, the other not, in order to support `getResource/getResourceAsStream` with
            // both absolute and relative paths
            writeResourceEntry(outputStream, new ByteArrayInputStream(resource), buffer, name);
        }

        String remappedResourceName = path != null ? remapper.maybeRelocateResource(path) : null;
        if (remappedResourceName != null) {
            String newFileName = remappedResourceName + name.substring(i);
            writeResourceEntry(outputStream, new ByteArrayInputStream(resource), buffer, newFileName);
        }
    }

    private void writeResourceEntry(ZipOutputStream outputStream, InputStream inputStream, byte[] buffer, String resourceFileName) throws IOException {
        outputStream.putNextEntry(newZipEntryWithFixedTime(resourceFileName));
        pipe(inputStream, outputStream, buffer);
        outputStream.closeEntry();
    }

    private void writeEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        ZipEntry zipEntry = newZipEntryWithFixedTime(name);
        outputStream.putNextEntry(zipEntry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private ZipEntry newZipEntryWithFixedTime(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES);
        return entry;
    }

    private void processClassFile(ZipOutputStream outputStream, ClasspathEntryVisitor.Entry entry) throws IOException {
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

        writeEntry(outputStream, newFileName, remappedClass);
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

    private void pipe(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        int read = inputStream.read(buffer);
        while (read != -1) {
            outputStream.write(buffer, 0, read);
            read = inputStream.read(buffer);
        }
    }

    private static class ShadingClassRemapper extends ClassRemapper {
        Map<String, String> remappedClassLiterals;
        private final ImplementationDependencyRelocator remapper;

        public ShadingClassRemapper(ClassWriter classWriter, ImplementationDependencyRelocator remapper) {
            super(classWriter, remapper);
            this.remapper = remapper;
            remappedClassLiterals = new HashMap<>();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            ImplementationDependencyRelocator.ClassLiteralRemapping remapping = null;
            if (CLASS_DESC.equals(desc)) {
                remapping = remapper.maybeRemap(name);
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
                            literal = remapper.maybeRelocateResource((String) cst);
                        }
                        if (literal == null) {
                            // tries to relocate literals in the form of foo.bar.Bar
                            literal = remapper.maybeRelocateResource(((String) cst).replace('.', '/'));
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
                        ImplementationDependencyRelocator.ClassLiteralRemapping remapping = remapper.maybeRemap(name);
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

    private String[] maybeRelocateResources(String... resources) {
        return asList(resources).stream()
            .filter(Objects::nonNull)
            .map(remapper::maybeRelocateResource)
            .filter(Objects::nonNull)
            .toArray(String[]::new);
    }

    private String[] separateLines(String entry) {
        return entry.split("\\n");
    }
}
