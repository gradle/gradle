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

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.ErroringAction;
import org.gradle.jvm.tasks.api.internal.ApiClassExtractor;
import org.gradle.util.internal.Java9ClassReader;
import org.objectweb.asm.ClassReader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
public class ApiJar extends DefaultTask {

    private Set<String> exportedPackages;
    private File outputFile;

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
        final File[] sourceFiles = sortedSourceFiles();
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
                        ClassReader classReader = new Java9ClassReader(readFileToByteArray(sourceFile));
                        if (!apiClassExtractor.shouldExtractApiClassFrom(classReader)) {
                            continue;
                        }

                        byte[] apiClassBytes = apiClassExtractor.extractApiClassFrom(classReader);
                        String internalClassName = classReader.getClassName();
                        String entryPath = internalClassName + ".class";
                        writeEntry(jos, entryPath, apiClassBytes);
                    }
                }

                private void writeEntry(JarOutputStream jos, String name, byte[] bytes) throws IOException {
                    JarEntry je = new JarEntry(name);
                    // Setting time to 0 because we need API jars to be identical independently of
                    // the timestamps of class files
                    je.setTime(0);
                    je.setSize(bytes.length);
                    jos.putNextEntry(je);
                    jos.write(bytes);
                    jos.closeEntry();
                }
            }
        );
    }

    private boolean isClassFile(File file) {
        return hasExtension(file, ".class");
    }

    private File[] sortedSourceFiles() {
        final File[] sourceFiles = (File[]) getInputs().getSourceFiles().asType(File[].class);
        Arrays.sort(sourceFiles);
        return sourceFiles;
    }
}
