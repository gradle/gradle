/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.tools.zip.ZipFile;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AnnotationProcessorDetector {
    private final FileCollectionFactory fileCollectionFactory;

    public AnnotationProcessorDetector(FileCollectionFactory fileCollectionFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
    }

    /**
     * Calculates the annotation processor path to use given some compile options and compile classpath.
     *
     * @return An empty collection when annotation processing should not be performed, non-empty when it should.
     */
    public FileCollection getEffectiveAnnotationProcessorClasspath(CompileOptions compileOptions, final FileCollection compileClasspath) {
        if (compileOptions.getCompilerArgs().contains("-proc:none")) {
            return fileCollectionFactory.empty("annotation processor path");
        }
        if (compileOptions.getAnnotationProcessorPath() != null) {
            return compileOptions.getAnnotationProcessorPath();
        }
        int pos = compileOptions.getCompilerArgs().indexOf("-processorpath");
        if (pos >= 0) {
            if (pos == compileOptions.getCompilerArgs().size() - 1) {
                throw new InvalidUserDataException("No path provided for compiler argument -processorpath in requested compiler args: " + Joiner.on(" ").join(compileOptions.getCompilerArgs()));
            }
            List<File> files = new ArrayList<File>();
            for (String path : Splitter.on(File.pathSeparatorChar).splitToList(compileOptions.getCompilerArgs().get(pos + 1))) {
                files.add(new File(path));
            }
            return fileCollectionFactory.fixed("annotation processor path", files);
        }

        return fileCollectionFactory.create(compileClasspath.getBuildDependencies(), new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                for (File file : compileClasspath) {
                    if (file.isDirectory() && new File(file, "META-INF/services/javax.annotation.processing.Processor").isFile()) {
                        return compileClasspath.getFiles();
                    }
                    if (file.isFile()) {
                        try {
                            ZipFile zipFile = new ZipFile(file);
                            try {
                                if (zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor") != null) {
                                    return compileClasspath.getFiles();
                                }
                            } finally {
                                zipFile.close();
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException("Could not read service definition from JAR " + file, e);
                        }
                    }
                }
                return Collections.emptySet();
            }

            @Override
            public String getDisplayName() {
                return "annotation processor path";
            }
        });
    }

}
