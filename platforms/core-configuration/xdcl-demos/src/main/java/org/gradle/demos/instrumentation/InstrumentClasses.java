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
import org.gradle.api.file.FileSystemOperations;
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

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * The XDCL port of {@code InstrumentClassesProjectFeaturePlugin.InstrumentClasses}: consumes a source
 * set's compiled classes and produces an instrumented copy of them. The original is an empty abstract
 * task with no action (it only declared inputs/outputs); this demo gives it a real {@code @TaskAction}
 * that runs every {@code .class} file through ASM ({@link LogMethodVisitor}, which injects a logging
 * statement into {@code greeting} methods) and writes the transformed bytecode to the output directory.
 * Non-class files are copied through unchanged.
 *
 * <p>The {@link ClassWriter} is created with {@link ClassWriter#COMPUTE_MAXS} so the injected stack
 * operations are accounted for; the injection adds no branches, so existing stack-map frames remain valid.
 *
 * <p>Cacheable: a {@code @Classpath} classes input plus an optional config input fully determine the
 * output directory.
 */
@CacheableTask
public abstract class InstrumentClasses extends DefaultTask {

    /** The raw compiled classes to instrument; wired from the source set's {@code JavaClasses.classesDir}. */
    @InputFiles
    @Classpath
    public abstract DirectoryProperty getClassesDir();

    /** An optional instrumentation configuration file (declared but consumed only as an input fingerprint). */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    /** The directory the instrumented classes are written to. */
    @OutputDirectory
    public abstract DirectoryProperty getInstrumentedClassesDir();

    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    void instrument() {
        File outDir = getInstrumentedClassesDir().get().getAsFile();
        // Replace any prior output so a removed input class does not linger (the output is fully derived).
        getFs().delete(spec -> spec.delete(outDir));

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
            // COMPUTE_MAXS recomputes max stack/locals for the injected instructions; the injection adds no
            // branches, so the existing stack-map frames stay valid and need no recomputation.
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new LogMethodVisitor(writer), 0);
            Files.write(target.toPath(), writer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to instrument " + source, e);
        }
    }
}
