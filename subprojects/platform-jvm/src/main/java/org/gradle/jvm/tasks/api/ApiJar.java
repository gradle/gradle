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

import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.normalization.java.ApiClassExtractor;
import org.objectweb.asm.ClassReader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.gradle.internal.FileUtils.hasExtension;
import static org.gradle.internal.IoActions.withResource;

/**
 * Assembles an "API Jar" containing only the members of a library's public API.
 *
 * <p>This task and the Jar it produces are designed primarily for internal use in support
 * of Gradle's "compile avoidance" performance feature. The task is automatically included
 * in the task graph for any JVM library that declares an {@code api { ... }}
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
 * <p>The inputs to this task are Java class files which must be provided via
 * {@link org.gradle.api.tasks.TaskInputs}.</p>
 *
 * @since 2.10
 * @see org.gradle.jvm.plugins.JvmComponentPlugin
 */
@Incubating
@Deprecated
public class ApiJar extends SourceTask {

    private Set<String> exportedPackages;
    private File outputFile;

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return super.getSource();
    }

    @Input
    public Set<String> getExportedPackages() {
        return exportedPackages;
    }

    public void setExportedPackages(Set<String> exportedPackages) {
        this.exportedPackages = exportedPackages;
    }

    @OutputFile
    public File getOutputFile() {
       return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void createApiJar() throws IOException {
        // Make sure all entries are always written in the same order
        final List<File> sourceFiles = sortedSourceFiles();
        final ApiClassExtractor apiClassExtractor = new ApiClassExtractor(getExportedPackages());
        withResource(
            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(getOutputFile()), 65536)),
            new ErroringAction<JarOutputStream>() {
                @Override
                protected void doExecute(final JarOutputStream jos) throws Exception {
                    writeManifest(jos);
                    writeClasses(jos);
                }

                private void writeManifest(JarOutputStream jos) throws IOException {
                    writeEntry(jos, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
                }

                private void writeClasses(JarOutputStream jos) throws Exception {
                    for (File sourceFile : sourceFiles) {
                        if (!isClassFile(sourceFile)) {
                            continue;
                        }
                        ClassReader classReader = new ClassReader(readFileToByteArray(sourceFile));
                        apiClassExtractor.extractApiClassFrom(classReader)
                            .ifPresent(apiClassBytes -> {
                                String internalClassName = classReader.getClassName();
                                String entryPath = internalClassName + ".class";
                                writeEntry(jos, entryPath, apiClassBytes);
                            });
                    }
                }

                private void writeEntry(JarOutputStream jos, String name, byte[] bytes) {
                    try {
                        JarEntry je = new JarEntry(name);
                        // Setting time to 0 because we need API jars to be identical independently of
                        // the timestamps of class files
                        je.setTime(0);
                        je.setSize(bytes.length);
                        jos.putNextEntry(je);
                        jos.write(bytes);
                        jos.closeEntry();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            }
        );
    }

    private boolean isClassFile(File file) {
        return hasExtension(file, ".class");
    }

    private List<File> sortedSourceFiles() {
        List<File> sourceFiles = Lists.newArrayList(getSource().getFiles());
        Collections.sort(sourceFiles);
        return sourceFiles;
    }
}
