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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class RuntimeShadedJarCreator {

    private static final int BUFFER_SIZE = 8192;
    private static final String SERVICES_DIR_PREFIX = "META-INF/services/";
    private static final int ADDITIONAL_PROGRESS_STEPS = 2;
    private static final ImplementationDependencyRelocator REMAPPER = new ImplementationDependencyRelocator();
    private static final String CLASS_DESC = "Ljava/lang/Class;";

    private final ProgressLoggerFactory progressLoggerFactory;

    public RuntimeShadedJarCreator(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public void create(final File outputJar, final Iterable<? extends File> files) {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(RuntimeShadedJarCreator.class);
        progressLogger.setDescription("Gradle JARs generation");
        progressLogger.setLoggingHeader("Generating JAR file '" + outputJar.getName() + "'");
        progressLogger.started();

        try {
            createFatJar(outputJar, files, progressLogger);
        } finally {
            progressLogger.completed();
        }
    }

    private void createFatJar(final File outputJar, final Iterable<? extends File> files, final ProgressLogger progressLogger) {
        final File tmpFile;

        try {
            tmpFile = File.createTempFile(outputJar.getName(), ".tmp");
            tmpFile.deleteOnExit();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        IoActions.withResource(openJarOutputStream(tmpFile), new ErroringAction<ZipOutputStream>() {
            @Override
            protected void doExecute(ZipOutputStream jarOutputStream) throws Exception {
                processFiles(jarOutputStream, files, new byte[BUFFER_SIZE], new HashSet<String>(), new HashMap<String, List<String>>(), progressLogger);
                jarOutputStream.finish();
            }
        });

        GFileUtils.moveFile(tmpFile, outputJar);
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

    private void processFiles(ZipOutputStream outputStream, Iterable<? extends File> files, byte[] buffer, HashSet<String> seenPaths, Map<String, List<String>> services,
                              ProgressLogger progressLogger) throws Exception {
        PercentageProgressFormatter progressFormatter = new PercentageProgressFormatter("Generating", Iterables.size(files) + ADDITIONAL_PROGRESS_STEPS);

        for (File file : files) {
            progressLogger.progress(progressFormatter.getProgress());

            if (file.getName().endsWith(".jar")) {
                processJarFile(outputStream, file, buffer, seenPaths, services);
            } else {
                processDirectory(outputStream, file, buffer, seenPaths, services);
            }

            progressFormatter.incrementAndGetProgress();
        }

        writeServiceFiles(outputStream, services);
        progressFormatter.incrementAndGetProgress();

        writeIdentifyingMarkerFile(outputStream);
        progressFormatter.incrementAndGetProgress();
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

    private void processDirectory(final ZipOutputStream outputStream, File file, final byte[] buffer, final HashSet<String> seenPaths, final Map<String, List<String>> services) {
        new DirectoryFileTree(file).visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                try {
                    ZipEntry zipEntry = new ZipEntry(dirDetails.getPath() + "/");
                    processEntry(outputStream, null, zipEntry, buffer, seenPaths, services);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                try {
                    ZipEntry zipEntry = new ZipEntry(fileDetails.getPath());
                    InputStream inputStream = fileDetails.open();
                    try {
                        processEntry(outputStream, inputStream, zipEntry, buffer, seenPaths, services);
                    } finally {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private void processJarFile(final ZipOutputStream outputStream, File file, final byte[] buffer, final Set<String> seenPaths, final Map<String, List<String>> services) throws IOException {
        IoActions.withResource(openJarFile(file), new ErroringAction<ZipInputStream>() {
            @Override
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry();
                while (zipEntry != null) {
                    processEntry(outputStream, inputStream, zipEntry, buffer, seenPaths, services);
                    zipEntry = inputStream.getNextEntry();
                }
            }
        });
    }

    private void processEntry(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer, final Set<String> seenPaths, Map<String, List<String>> services) throws IOException {
        String name = zipEntry.getName();
        if (zipEntry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
            return;
        }
        if (!name.startsWith(SERVICES_DIR_PREFIX) && !seenPaths.add(name)) {
            return;
        }

        if (name.endsWith(".class")) {
            processClassFile(outputStream, inputStream, zipEntry, buffer);
        } else if (name.startsWith(SERVICES_DIR_PREFIX)) {
            processServiceDescriptor(inputStream, zipEntry, buffer, services);
        } else {
            copyEntry(outputStream, inputStream, zipEntry, buffer);
        }
    }

    private void processServiceDescriptor(InputStream inputStream, ZipEntry zipEntry, byte[] buffer, Map<String, List<String>> services) throws IOException {
        String descriptorName = zipEntry.getName().substring(SERVICES_DIR_PREFIX.length());
        String descriptorApiClass = periodsToSlashes(descriptorName);
        String relocatedApiClassName = REMAPPER.maybeRelocateResource(descriptorApiClass);
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass;
        }

        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        String entry = new String(bytes, Charsets.UTF_8).replaceAll("(?m)^#.*", "").trim(); // clean up comments and new lines
        String descriptorImplClass = periodsToSlashes(entry);
        String relocatedImplClassName = REMAPPER.maybeRelocateResource(descriptorImplClass);
        if (relocatedImplClassName == null) {
            relocatedImplClassName = descriptorImplClass;
        }

        String serviceType = slashesToPeriods(relocatedApiClassName);
        String serviceProvider = slashesToPeriods(relocatedImplClassName).trim();

        if (!services.containsKey(serviceType)) {
            services.put(serviceType, Lists.newArrayList(serviceProvider));
        } else {
            List<String> providers = services.get(serviceType);
            providers.add(serviceProvider);
        }
    }

    private String slashesToPeriods(String slashClassName) {
        return slashClassName.replace('/', '.');
    }

    private String periodsToSlashes(String periodClassName) {
        return periodClassName.replace('.', '/');
    }

    private void copyEntry(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        pipe(inputStream, bos, buffer);
        String originalName = zipEntry.getName();
        byte[] resource = bos.toByteArray();

        int i = originalName.lastIndexOf("/");
        String path = i == -1 ? null : originalName.substring(0, i);

        if (REMAPPER.keepOriginalResource(path)) {
            // we're writing 2 copies of the resource: one relocated, the other not, in order to support `getResource/getResourceAsStream` with
            // both absolute and relative paths
            writeResourceEntry(outputStream, new ByteArrayInputStream(resource), buffer, zipEntry.getName());
        }

        String remappedResourceName = path != null ? REMAPPER.maybeRelocateResource(path) : null;
        if (remappedResourceName != null) {
            String newFileName = remappedResourceName + originalName.substring(i);
            writeResourceEntry(outputStream, new ByteArrayInputStream(resource), buffer, newFileName);
        }
    }

    private void writeResourceEntry(ZipOutputStream outputStream, InputStream inputStream, byte[] buffer, String resourceFileName) throws IOException {
        outputStream.putNextEntry(new ZipEntry(resourceFileName));
        pipe(inputStream, outputStream, buffer);
        outputStream.closeEntry();
    }

    private void writeEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        ZipEntry zipEntry = new ZipEntry(name);
        outputStream.putNextEntry(zipEntry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private void processClassFile(ZipOutputStream outputStream, InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        String className = zipEntry.getName().substring(0, zipEntry.getName().length() - ".class".length());
        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        byte[] remappedClass = remapClass(className, bytes);

        String remappedClassName = REMAPPER.maybeRelocateResource(className);
        String newFileName = (remappedClassName == null ? className : remappedClassName).concat(".class");

        writeEntry(outputStream, newFileName, remappedClass);
    }

    private byte[] remapClass(String className, byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor remappingVisitor = new ShadingClassRemapper(classWriter);

        try {
            classReader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new GradleException("Error in ASM processing class: " + className, e);
        }

        return classWriter.toByteArray();
    }

    private byte[] readEntry(InputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        int size = (int) zipEntry.getSize();
        if (size == -1) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(buffer.length);
            int read = inputStream.read(buffer);
            while (read != -1) {
                out.write(buffer, 0, read);
                read = inputStream.read(buffer);
            }
            return out.toByteArray();
        } else {
            byte[] bytes = new byte[size];
            int read = inputStream.read(bytes);
            while (read < size) {
                read += inputStream.read(bytes, read, size - read);
            }
            return bytes;
        }
    }

    private void pipe(InputStream inputStream, OutputStream outputStream, byte[] buffer) throws IOException {
        int read = inputStream.read(buffer);
        while (read != -1) {
            outputStream.write(buffer, 0, read);
            read = inputStream.read(buffer);
        }
    }

    private ZipInputStream openJarFile(File file) throws IOException {
        return new ZipInputStream(new FileInputStream(file));
    }

    private static class ShadingClassRemapper extends ClassRemapper {
        Map<String, String> remappedClassLiterals;

        public ShadingClassRemapper(ClassWriter classWriter) {
            super(classWriter, RuntimeShadedJarCreator.REMAPPER);
            remappedClassLiterals = new HashMap<String, String>();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            ImplementationDependencyRelocator.ClassLiteralRemapping remapping = null;
            if (CLASS_DESC.equals(desc)) {
                remapping = REMAPPER.maybeRemap(name);
                if (remapping != null) {
                    remappedClassLiterals.put(remapping.getLiteral(), remapping.getLiteralReplacement().replace("/", "."));
                }
            }
            return super.visitField(access, remapping != null ? remapping.getFieldNameReplacement() : name, desc, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {
                @Override
                public void visitLdcInsn(Object cst) {
                    if (cst instanceof String) {
                        String literal = remappedClassLiterals.get(cst);
                        super.visitLdcInsn(literal != null ? literal : cst);
                    } else {
                        super.visitLdcInsn(cst);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) && CLASS_DESC.equals(desc)) {
                        ImplementationDependencyRelocator.ClassLiteralRemapping remapping = REMAPPER.maybeRemap(name);
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
}
