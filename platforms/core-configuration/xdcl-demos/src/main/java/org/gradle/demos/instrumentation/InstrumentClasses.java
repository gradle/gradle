/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.instrumentation;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * Consumes a source set's compiled classes and produces an instrumented copy of them.
 */
@CacheableTask
public abstract class InstrumentClasses extends DefaultTask {

    /** The raw compiled classes to instrument; wired from the source set's {@code JavaClasses.classesDir}. */
    @InputFiles
    @Classpath
    public abstract DirectoryProperty getClassesDir();

    /** An optional instrumentation configuration file (unimplemented). */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    /** The directory the instrumented classes are written to. */
    @OutputDirectory
    public abstract DirectoryProperty getInstrumentedClassesDir();

    @TaskAction
    void instrument() {
        File outDir = getInstrumentedClassesDir().get().getAsFile();

        getClassesDir().get().getAsFileTree().visit(details -> {
            if (details.isDirectory()) {
                return;
            }
            File target = getInstrumentedClassesDir().get().file(details.getPath()).getAsFile();
            //noinspection ResultOfMethodCallIgnored
            target.getParentFile().mkdirs();
            if (details.getName().endsWith(".class")) {
                transform(details.getFile(), target);
            } else {
                // Copy through anything that is not bytecode (resources do not reach the classes dir, but
                // be defensive) so the output is a complete, self-contained classes directory.
                details.copyTo(target);
            }
        });
    }

    private static void transform(File source, File target) {
        try {
            ClassReader reader = new ClassReader(Files.readAllBytes(source.toPath()));
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new LogMethodVisitor(writer), 0);
            Files.write(target.toPath(), writer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to instrument " + source, e);
        }
    }
}
