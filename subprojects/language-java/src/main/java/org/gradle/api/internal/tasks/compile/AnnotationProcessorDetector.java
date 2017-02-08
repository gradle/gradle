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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.FileContentCache;
import org.gradle.api.internal.cache.FileContentCacheFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AnnotationProcessorDetector {
    private final FileCollectionFactory fileCollectionFactory;
    private final FileContentCache<Boolean> cache;

    public AnnotationProcessorDetector(FileCollectionFactory fileCollectionFactory, FileContentCacheFactory cacheFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
        cache = cacheFactory.newCache("annotation-processors", 20000, new AnnotationServiceLocator(), BaseSerializerFactory.BOOLEAN_SERIALIZER);
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

        return fileCollectionFactory.create(new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(compileClasspath);
            }
        }, new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                for (File file : compileClasspath) {
                    boolean hasServices = cache.get(file);
                    if (hasServices) {
                        return compileClasspath.getFiles();
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

    private static class AnnotationServiceLocator implements FileContentCacheFactory.Calculator<Boolean> {
        @Override
        public Boolean calculate(File file, FileType fileType) {
            if (fileType == FileType.Directory) {
                return new File(file, "META-INF/services/javax.annotation.processing.Processor").isFile();
            }

            if (fileType == FileType.RegularFile && FileUtils.isJar(file.getName())) {
                try {
                    ZipFile zipFile = new ZipFile(file);
                    try {
                        return zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor") != null;
                    } finally {
                        zipFile.close();
                    }
                } catch (IOException e) {
                    DeprecationLogger.nagUserWith("Malformed jar [" + file.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on compile classpath.");
                }
            }

            return false;
        }
    }
}
