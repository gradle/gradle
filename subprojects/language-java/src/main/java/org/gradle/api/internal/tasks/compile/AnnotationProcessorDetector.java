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
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.cache.internal.FileContentCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.serialize.BaseSerializerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnnotationProcessorDetector {
    private final FileCollectionFactory fileCollectionFactory;
    private final FileContentCache<Map<String, String>> cache;

    public AnnotationProcessorDetector(FileCollectionFactory fileCollectionFactory, FileContentCacheFactory cacheFactory) {
        this.fileCollectionFactory = fileCollectionFactory;
        cache = cacheFactory.newCache("annotation-processors-info", 20000, new AnnotationProcessorScanner(), BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER);
    }

    /**
     * Calculates the annotation processor path to use given some compile options and compile classpath.
     *
     * @return An empty collection when annotation processing should not be performed, non-empty when it should.
     */
    public FileCollection getEffectiveAnnotationProcessorClasspath(final CompileOptions compileOptions, final FileCollection compileClasspath) {
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
        if (compileClasspath == null) {
            return null;
        }
        if (checkExplicitProcessorOption(compileOptions)) {
            return compileClasspath;
        }
        return fileCollectionFactory.create(new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(compileClasspath);
            }
        }, new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                // TODO:  This returns the entire compile classpath if any element of the
                // compile classpath contains an annotation processor.  Why not just return
                // the subset of elements that have annotation processors?
                for (File file : compileClasspath) {
                    if ((new AnnotationProcessorInfo(cache.get(file))).isProcessor()) {
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

    public Set<AnnotationProcessorInfo> getAnnotationProcessorInfo(final CompileOptions compileOptions, final FileCollection compileClasspath) {

        FileCollection effectiveAnnotationProcessorPath = getEffectiveAnnotationProcessorClasspath(compileOptions, compileClasspath);
        Set<AnnotationProcessorInfo> result = Sets.newHashSet();
        for (File file : effectiveAnnotationProcessorPath) {
            Map<String, String> props = cache.get(file);
            if (props != null) {
                AnnotationProcessorInfo info = new AnnotationProcessorInfo(props);
                if (info.isProcessor()) {
                    result.add(info);
                }
            }
        }
        return result;
    }

    private static boolean checkExplicitProcessorOption(CompileOptions compileOptions) {
        boolean hasExplicitProcessor = false;
        int pos = compileOptions.getCompilerArgs().indexOf("-processor");
        if (pos >= 0) {
            if (pos == compileOptions.getCompilerArgs().size() - 1) {
                throw new InvalidUserDataException("No processor specified for compiler argument -processor in requested compiler args: " + Joiner.on(" ").join(compileOptions.getCompilerArgs()));
            }
            hasExplicitProcessor = true;
        }
        return hasExplicitProcessor;
    }
}
