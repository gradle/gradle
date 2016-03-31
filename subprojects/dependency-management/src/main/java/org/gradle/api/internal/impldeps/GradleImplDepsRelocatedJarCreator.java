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

package org.gradle.api.internal.impldeps;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.installation.GradleFatJar;
import org.gradle.internal.progress.PercentageProgressFormatter;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.GFileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.RemappingClassAdapter;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GradleImplDepsRelocatedJarCreator implements RelocatedJarCreator {

    private static final int BUFFER_SIZE = 8192;
    private static final String SERVICES_DIR_PREFIX = "META-INF/services/";
    private static final int ADDITIONAL_PROGRESS_STEPS = 2;
    private static final GradleImplDepsRelocator REMAPPER = new GradleImplDepsRelocator();
    private final ProgressLoggerFactory progressLoggerFactory;

    public GradleImplDepsRelocatedJarCreator(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public void create(final File outputJar, final Iterable<? extends File> files) {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(GradleImplDepsRelocatedJarCreator.class);
        progressLogger.setDescription("Gradle JARs generation");
        progressLogger.setLoggingHeader(String.format("Generating JAR file '%s'", outputJar.getName()));
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
                throw new RuntimeException("non JAR on classpath: " + file.getAbsolutePath());
            }

            progressFormatter.incrementAndGetProgress();
        }

        writeServiceFiles(outputStream, services);
        progressFormatter.incrementAndGetProgress();

        writeMarkerFile(outputStream);
        progressFormatter.incrementAndGetProgress();
    }

    private void writeServiceFiles(ZipOutputStream outputStream, Map<String, List<String>> services) throws IOException {
        for (Map.Entry<String, List<String>> service : services.entrySet()) {
            String allProviders = Joiner.on("\n").join(service.getValue());
            writeEntry(outputStream, SERVICES_DIR_PREFIX + service.getKey(), allProviders.getBytes(Charsets.UTF_8));
        }
    }

    private void writeMarkerFile(ZipOutputStream outputStream) throws IOException {
        writeEntry(outputStream, GradleFatJar.MARKER_FILENAME, new byte[0]);
    }

    private void processJarFile(final ZipOutputStream outputStream, File file, final byte[] buffer, final HashSet<String> seenPaths, final Map<String, List<String>> services) throws IOException {
        IoActions.withResource(openJarFile(file), new ErroringAction<ZipInputStream>() {
            @Override
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry();
                while (zipEntry != null) {
                    String name = zipEntry.getName();
                    if (name.startsWith(SERVICES_DIR_PREFIX) || seenPaths.add(name)) {
                        processEntry(outputStream, inputStream, zipEntry, buffer, services);
                    }
                    zipEntry = inputStream.getNextEntry();
                }
            }
        });
    }

    private void processEntry(ZipOutputStream outputStream, ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer, Map<String, List<String>> services) throws IOException {
        String name = zipEntry.getName();

        if (zipEntry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
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

    private void processServiceDescriptor(ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer, Map<String, List<String>> services) throws IOException {
        String descriptorName = zipEntry.getName().substring(SERVICES_DIR_PREFIX.length());
        String descriptorApiClass = periodsToSlashes(descriptorName);
        String relocatedApiClassName = REMAPPER.relocateClass(descriptorApiClass);
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass;
        }

        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        String entry = new String(bytes, Charsets.UTF_8).replaceAll("(?m)^#.*", "").trim(); // clean up comments and new lines
        String descriptorImplClass = periodsToSlashes(entry);
        String relocatedImplClassName = REMAPPER.relocateClass(descriptorImplClass);
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

    private void copyEntry(ZipOutputStream outputStream, ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        outputStream.putNextEntry(new ZipEntry(zipEntry.getName()));
        pipe(inputStream, outputStream, buffer);
        outputStream.closeEntry();
    }

    private void writeEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        ZipEntry zipEntry = new ZipEntry(name);
        outputStream.putNextEntry(zipEntry);
        outputStream.write(content);
        outputStream.closeEntry();
    }

    private void processClassFile(ZipOutputStream outputStream, ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        String className = zipEntry.getName().substring(0, zipEntry.getName().length() - ".class".length());
        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        ClassVisitor remappingVisitor = new RemappingClassAdapter(classWriter, REMAPPER);

        try {
            classReader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) {
            throw new GradleException("Error in ASM processing class: " + className, e);
        }

        byte[] remappedClass = classWriter.toByteArray();

        String remappedClassName = REMAPPER.relocateClass(className);
        String newFileName = (remappedClassName == null ? className : remappedClassName).concat(".class");

        writeEntry(outputStream, newFileName, remappedClass);
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

}