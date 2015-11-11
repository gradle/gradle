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

package org.gradle.jvm.tasks.api;

import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.jvm.tasks.api.internal.ApiClassExtractor;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Assembles an "API Jar" containing only the members of a library's public API.
 *
 * <p>This task and the Jar it produces are designed primarily for internal use in support
 * of Gradle's "compile avoidance" performance feature. The task is automatically included
 * in the task graph for any JVM libary that declares an {@code api { ... }}
 * specification, and the resulting Jar will automatically be placed on the compile time
 * classpath of projects that depend on the library in lieu of the library's complete
 * so-called "Runtime Jar".</p>
 *
 * <p>Swapping the API Jar in for the Runtime Jar at compile time is what makes
 * "compile avoidance" possible: because the contents of the API Jar change only when
 * actual API changes are made, the API Jar passes Gradle's up-to-date checks, even if the
 * implementation in the Runtime Jar has changed. Ultimately, this means that projects
 * depending on the library in question will need to recompile potentially far less often.
 * </p>
 *
 * <p>In order to ensure that API Jars change as infrequently as possible, this task and
 * its supporting classes ensure that only actual public API members are included in the
 * API Jar, and that the methods among those members are stripped of their implementation.
 * Because the members included in API Jars exist only for compilation purposes, they need
 * no actual implementation, and for this reason, all such methods throw
 * {@link UnsupportedOperationException} in the unlikely event that they are present on
 * the classpath and invoked at runtime.</p>
 *
 * @since 2.10
 * @see org.gradle.jvm.plugins.JvmComponentPlugin
 */
@Incubating
public class ApiJar extends DefaultTask {

    private Set<String> exportedPackages;
    private File runtimeClassesDir;
    private File destinationDir;
    private String archiveName;
    private File apiClassesDir;

    @Input
    public Set<String> getExportedPackages() {
        return exportedPackages;
    }

    public void setExportedPackages(Set<String> exportedPackages) {
        this.exportedPackages = exportedPackages;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    // Not an @OutputDirectory in order to avoid up-to-date checks
    public File getApiClassesDir() {
        return apiClassesDir;
    }

    public void setApiClassesDir(File apiClassesDir) {
        this.apiClassesDir = apiClassesDir;
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
    public void createApiJar(final IncrementalTaskInputs inputs) throws Exception {
        final File archivePath = new File(destinationDir, archiveName);
        if (!inputs.isIncremental()) {
            FileUtils.deleteQuietly(archivePath);
            FileUtils.deleteDirectory(apiClassesDir);
        }
        destinationDir.mkdirs();
        apiClassesDir.mkdirs();
        final ApiClassExtractor apiClassExtractor = new ApiClassExtractor(exportedPackages);
        final AtomicBoolean updated = new AtomicBoolean();
        final Map<File, byte[]> apiClasses = Maps.newHashMap();
        inputs.outOfDate(new ErroringAction<InputFileDetails>() {
            @Override
            protected void doExecute(InputFileDetails inputFileDetails) throws Exception {
                updated.set(true);
                File originalClassFile = inputFileDetails.getFile();
                if (!apiClassExtractor.shouldExtractApiClassFrom(originalClassFile)) {
                    return;
                }
                final byte[] apiClassBytes = apiClassExtractor.extractApiClassFrom(originalClassFile);
                apiClasses.put(originalClassFile, apiClassBytes);
                File apiClassFile = apiClassFileFor(originalClassFile);
                apiClassFile.getParentFile().mkdirs();
                IoActions.withResource(new FileOutputStream(apiClassFile), new ErroringAction<OutputStream>() {
                    @Override
                    protected void doExecute(OutputStream outputStream) throws Exception {
                        outputStream.write(apiClassBytes);
                    }
                });
            }
        });
        inputs.removed(new ErroringAction<InputFileDetails>() {
            @Override
            protected void doExecute(InputFileDetails removedOriginalClassFile) throws Exception {
                updated.set(true);
                deleteApiClassFileFor(removedOriginalClassFile.getFile());
            }
        });
        if (updated.get()) {
            IoActions.withResource(
                new JarOutputStream(new BufferedOutputStream(new FileOutputStream(archivePath), 65536)),
                new ErroringAction<JarOutputStream>() {
                    private final SortedMap<String, File> sortedFiles = Maps.newTreeMap();

                    private void writeEntries(JarOutputStream jos) throws Exception {
                        for (Map.Entry<String, File> entry : sortedFiles.entrySet()) {
                            JarEntry je = new JarEntry(entry.getKey());
                            // Setting time to 0 because we need API jars to be identical independently of
                            // the timestamps of class files
                            je.setTime(0);
                            File originalClassFile = entry.getValue();
                            // get it from cache if it has just been converted
                            byte[] apiClassBytes = apiClasses.get(originalClassFile);
                            if (apiClassBytes == null) {
                                // or get it from disk otherwise
                                apiClassBytes = FileUtils.readFileToByteArray(originalClassFile);
                            }
                            je.setSize(apiClassBytes.length);
                            jos.putNextEntry(je);
                            jos.write(apiClassBytes);
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
                }
            );
        }
    }

    private File apiClassFileFor(File originalClassFile) {
        StringBuilder sb = new StringBuilder(originalClassFile.getName());
        File cur = originalClassFile.getParentFile();
        while (!cur.equals(runtimeClassesDir)) {
            sb.insert(0, cur.getName() + File.separator);
            cur = cur.getParentFile();
        }
        return new File(apiClassesDir, sb.toString());
    }

    private void deleteApiClassFileFor(File originalClassFile) {
        File apiClassFile = apiClassFileFor(originalClassFile);
        if (apiClassFile.exists()) {
            FileUtils.deleteQuietly(apiClassFile);
        }
    }
}
