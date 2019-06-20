/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java.internal;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.internal.tasks.compile.tooling.JavaCompileTaskSuccessResultPostProcessor;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.SubscribableBuildActionRunnerRegistration;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static java.util.Collections.emptyList;

public class JavaLanguagePluginServiceRegistry extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new JavaGlobalScopeServices());
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.addProvider(new JavaGradleScopeServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new JavaProjectScopeServices());
    }

    private static class JavaGlobalScopeServices {
        SubscribableBuildActionRunnerRegistration createJavaSubscribableBuildActionRunnerRegistration(final JavaCompileTaskSuccessResultPostProcessor factory) {
            return new SubscribableBuildActionRunnerRegistration() {
                @Override
                public Iterable<Object> createListeners(BuildClientSubscriptions clientSubscriptions, BuildEventConsumer consumer) {
                    if (clientSubscriptions.isRequested(OperationType.TASK)) {
                        return Collections.<Object>singletonList(factory);
                    }
                    return emptyList();
                }
            };
        }

        public JavaCompileTaskSuccessResultPostProcessor createJavaCompileTaskSuccessResultDecoratorFactory() {
            return new JavaCompileTaskSuccessResultPostProcessor();
        }
    }

    private static class JavaGradleScopeServices {
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary.class)
                .registerArtifactType(JavadocArtifact.class, ArtifactType.JAVADOC);
        }

        public AnnotationProcessorDetector createAnnotationProcessorDetector(FileContentCacheFactory cacheFactory, LoggingConfiguration loggingConfiguration) {
            return new AnnotationProcessorDetector(cacheFactory, LoggerFactory.getLogger(AnnotationProcessorDetector.class), loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS);
        }
    }

    private static class JavaProjectScopeServices {
        public IncrementalCompilerFactory createIncrementalCompilerFactory(FileOperations fileOperations, StreamHasher streamHasher, GeneralCompileCaches compileCaches, BuildOperationExecutor buildOperationExecutor, StringInterner interner, FileSystemSnapshotter fileSystemSnapshotter, FileHasher fileHasher) {
            return new IncrementalCompilerFactory(fileOperations, streamHasher, compileCaches, buildOperationExecutor, interner, fileSystemSnapshotter, fileHasher);
        }
    }
}
