/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.runtimeshaded;

import org.gradle.cache.internal.GeneratedGradleJarCache;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

@ServiceScope(Scope.Build.class)
public class RuntimeShadedJarFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeShadedJarFactory.class);

    private final GeneratedGradleJarCache cache;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final BuildOperationRunner buildOperationRunner;

    public RuntimeShadedJarFactory(GeneratedGradleJarCache cache, ProgressLoggerFactory progressLoggerFactory, ClasspathWalker classpathWalker, ClasspathBuilder classpathBuilder, BuildOperationRunner buildOperationRunner) {
        this.cache = cache;
        this.progressLoggerFactory = progressLoggerFactory;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.buildOperationRunner = buildOperationRunner;
    }

    public File get(final RuntimeShadedJarType type, final Collection<? extends File> classpath) {
        final File jarFile = cache.get(type.getIdentifier(), file -> buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                RuntimeShadedJarCreator creator = new RuntimeShadedJarCreator(
                    progressLoggerFactory,
                    new ImplementationDependencyRelocator(type),
                    classpathWalker,
                    classpathBuilder
                );
                creator.create(file, classpath);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Generate " + file)
                    .progressDisplayName("Generating " + file.getName());
            }
        }));
        LOGGER.debug("Using Gradle runtime shaded JAR file: {}", jarFile);
        return jarFile;
    }
}
