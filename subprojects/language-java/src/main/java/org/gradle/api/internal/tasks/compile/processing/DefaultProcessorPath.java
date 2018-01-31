/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.compile.CompileOptions;

/**
 * A wrapper that tells the {@link AnnotationProcessorPathFactory} that this is the new
 * default annotation processor path introduced in Gradle 4.6
 *
 * See {@link AnnotationProcessorPathFactory#getEffectiveAnnotationProcessorClasspath(CompileOptions, FileCollection)}
 * for more details.
 */
public class DefaultProcessorPath extends CompositeFileCollection {
    private final Configuration processorPath;

    public DefaultProcessorPath(Configuration processorPath) {
        this.processorPath = processorPath;
    }

    @Override
    public String getDisplayName() {
        return "annotation processor path";
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(processorPath);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(processorPath);
    }
}
