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
import org.gradle.api.GradleException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.RemappingClassAdapter;

import java.io.*;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GradleImplDepsRelocatedJarCreator implements RelocatedJarCreator {

    private static final int BUFFER_SIZE = 8192;
    private static final GradleImplDepsRelocator REMAPPER = new GradleImplDepsRelocator();

    public void create(File outputJar, final Iterable<? extends File> files) {
        IoActions.withResource(openJarOutputStream(outputJar), new ErroringAction<ZipOutputStream>() {
            @Override
            protected void doExecute(ZipOutputStream jarOutputStream) throws Exception {
                processFiles(jarOutputStream, files, new byte[BUFFER_SIZE], new HashSet<String>());
                jarOutputStream.finish();
            }
        });
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

    private void processFiles(ZipOutputStream outputStream, Iterable<? extends File> files, byte[] buffer, HashSet<String> seenPaths) throws Exception {
        for (File file : files) {
            if (file.getName().endsWith(".jar")) {
                processJarFile(outputStream, file, buffer, seenPaths);
            } else {
                throw new RuntimeException("non JAR on classpath: " + file.getAbsolutePath());
            }
        }
    }

    private void processJarFile(final ZipOutputStream outputStream, File file, final byte[] buffer, final HashSet<String> seenPaths) throws IOException {
        IoActions.withResource(openJarFile(file), new ErroringAction<ZipInputStream>() {
            @Override
            protected void doExecute(ZipInputStream inputStream) throws Exception {
                ZipEntry zipEntry = inputStream.getNextEntry();
                while (zipEntry != null) {
                    if (seenPaths.add(zipEntry.getName())) {
                        processEntry(outputStream, inputStream, zipEntry, buffer);
                    }
                    zipEntry = inputStream.getNextEntry();
                }
            }
        });
    }

    private void processEntry(ZipOutputStream outputStream, ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        if (zipEntry.isDirectory() || zipEntry.getName().equals("META-INF/MANIFEST.MF")) {
            return;
        }

        String name = zipEntry.getName();
        if (name.endsWith(".class")) {
            processClassFile(outputStream, inputStream, zipEntry, buffer);
        } else if (name.startsWith("META-INF/services/")) {
            processServiceDescriptor(outputStream, inputStream, zipEntry, buffer);
        } else {
            copyEntry(outputStream, inputStream, zipEntry, buffer);
        }
    }

    private void processServiceDescriptor(ZipOutputStream outputStream, ZipInputStream inputStream, ZipEntry zipEntry, byte[] buffer) throws IOException {
        String descriptorName = zipEntry.getName().substring("META-INF/services/".length());
        String descriptorApiClass = periodsToSlashes(descriptorName);
        String relocatedApiClassName = REMAPPER.relocateClass(descriptorApiClass);
        if (relocatedApiClassName == null) {
            relocatedApiClassName = descriptorApiClass;
        }

        byte[] bytes = readEntry(inputStream, zipEntry, buffer);
        String descriptorImplClass = periodsToSlashes(new String(bytes, Charsets.UTF_8));
        String relocatedImplClassName = REMAPPER.relocateClass(descriptorImplClass);
        if (relocatedImplClassName == null) {
            relocatedImplClassName = descriptorImplClass;
        }

        writeEntry(outputStream, "META-INF/services/" + slashesToPeriods(relocatedApiClassName), slashesToPeriods(relocatedImplClassName).getBytes(Charsets.UTF_8));
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