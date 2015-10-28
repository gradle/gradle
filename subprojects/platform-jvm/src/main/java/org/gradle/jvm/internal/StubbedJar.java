/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.language.base.internal.tasks.apigen.ApiStubGenerator;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class StubbedJar extends DefaultTask {

    private Collection<String> exportedPackages;
    private File runtimeClassesDir;
    private File destinationDir;
    private String archiveName;
    private File apiClassesDir;

    @Input
    public Collection<String> getExportedPackages() {
        return exportedPackages;
    }

    public void setExportedPackages(Collection<String> exportedPackages) {
        this.exportedPackages = exportedPackages;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    // This could be considered as an @OutputDirectory
    // however doing so would result in up-to-date checks, although this
    // should not happen: this is a permanent "cache" for stubbed API classes
    // and we cannot use the task temp directory because it is not guaranteed
    // to be kept among various invocations
    public File getApiClassesDir() {
        return apiClassesDir;
    }

    public void setApiClassesDir(File stubbedClassesDir) {
        this.apiClassesDir = stubbedClassesDir;
    }

    @InputDirectory
    @SkipWhenEmpty
    public File getRuntimeClassesDir() {
        return runtimeClassesDir;
    }

    public void setRuntimeClassesDir(File runtimeClassesDir) {
        this.runtimeClassesDir = runtimeClassesDir;
    }

    @Input
    public String getArchiveName() {
        return archiveName;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    @TaskAction
    void createStubs(final IncrementalTaskInputs inputs) throws Exception {
        final File archivePath = new File(destinationDir, archiveName);
        if (!inputs.isIncremental()) {
            FileUtils.deleteQuietly(archivePath);
            FileUtils.deleteDirectory(apiClassesDir);
        }
        destinationDir.mkdirs();
        apiClassesDir.mkdirs();
        final ApiStubGenerator stubGenerator = new ApiStubGenerator(ImmutableList.copyOf(getExportedPackages()));
        final AtomicBoolean updated = new AtomicBoolean();
        final Map<File, byte[]> convertedFiles = Maps.newHashMap();
        inputs.outOfDate(new ErroringAction<InputFileDetails>() {
            @Override
            protected void doExecute(InputFileDetails inputFileDetails) throws Exception {
                updated.set(true);
                File file = inputFileDetails.getFile();
                File stubFile = getStubFileFor(file);
                if (!isClass(file) || !stubGenerator.belongsToAPI(new FileInputStream(file))) {
                    deleteStub(stubFile);
                    return;
                }
                final byte[] bytes = stubGenerator.convertToApi(new FileInputStream(file));
                convertedFiles.put(file, bytes);
                stubFile.getParentFile().mkdirs();
                IoActions.withResource(new FileOutputStream(stubFile), new ErroringAction<FileOutputStream>() {
                    @Override
                    protected void doExecute(FileOutputStream fos) throws Exception {
                        fos.write(bytes);
                    }
                });

            }
        });
        inputs.removed(new ErroringAction<InputFileDetails>() {
            @Override
            protected void doExecute(InputFileDetails input) throws Exception {
                updated.set(true);
                deleteStub(getStubFileFor(input.getFile()));
            }
        });
        if (updated.get()) {
            IoActions.withResource(new JarOutputStream(new BufferedOutputStream(new FileOutputStream(archivePath), 65536)), new ErroringAction<JarOutputStream>() {
                private final SortedMap<String, File> sortedFiles = Maps.newTreeMap();

                private void writeEntries(JarOutputStream jos) throws Exception {
                    for (Map.Entry<String, File> entry : sortedFiles.entrySet()) {
                        JarEntry ze = new JarEntry(entry.getKey());
                        // Setting time to 0 because we need API jars to be identical independently of
                        // the timestamps of class files
                        ze.setTime(0);
                        File stubFile = entry.getValue();
                        // get it from cache if it has just been converted
                        byte[] stub = convertedFiles.get(stubFile);
                        if (stub==null) {
                            // or get it from disk otherwise
                            stub = FileUtils.readFileToByteArray(stubFile);
                        }
                        ze.setSize(stub.length);
                        jos.putNextEntry(ze);
                        jos.write(stub);
                        jos.closeEntry();
                    }
                }

                private void collectFiles(String relativePath, File f) throws Exception {
                    String path = "".equals(relativePath) ? f.getName() : relativePath + "/" + f.getName();
                    if (f.isFile()) {
                        sortedFiles.put(path, f);
                    } else if (f.isDirectory()) {
                        for (File file : f.listFiles()) {
                            String root = relativePath == null ? "" : path;
                            collectFiles(root, file);
                        }
                    }
                }

                @Override
                protected void doExecute(final JarOutputStream jos) throws Exception {
                    writeManifest(jos);
                    // Make sure all entries are always written in the same order
                    collectFiles(null, apiClassesDir);
                    writeEntries(jos);
                    jos.close();
                }

                private void writeManifest(JarOutputStream jos) throws IOException {
                    JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
                    je.setTime(0);
                    jos.putNextEntry(je);
                    jos.write("Manifest-Version: 1.0\n".getBytes());
                    jos.closeEntry();
                }
            });
        }
    }

    private void deleteStub(File stubFile) {
        if (stubFile.exists()) {
            FileUtils.deleteQuietly(stubFile);
        }
    }

    private File getStubFileFor(File file) {
        StringBuilder sb = new StringBuilder(file.getName());
        File cur = file.getParentFile();
        while (!cur.equals(runtimeClassesDir)) {
            sb.insert(0, cur.getName() + File.separator);
            cur = cur.getParentFile();
        }
        return new File(apiClassesDir, sb.toString());
    }

    private static boolean isClass(File file) {
        return file.getName().endsWith(".class");
    }

}
