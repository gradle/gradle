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

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.internal.tasks.compile.tooling.JavaCompileTaskSuccessResultPostProcessor;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.jvm.JvmLibrary;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.jvm.toolchain.internal.ToolchainToolFactory;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.tooling.events.OperationType;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static java.util.Collections.emptyList;

public class JavaLanguageServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new JavaGlobalScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new JavaBuildScopeServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new JavaProjectScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new ServiceRegistrationProvider() {
            @Provides
            public AnnotationProcessorDetector createAnnotationProcessorDetector(FileContentCacheFactory cacheFactory, LoggingConfiguration loggingConfiguration) {
                return new AnnotationProcessorDetector(cacheFactory, LoggerFactory.getLogger(AnnotationProcessorDetector.class), loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS);
            }
        });
    }

    private static class JavaGlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        OperationResultPostProcessorFactory createJavaSubscribableBuildActionRunnerRegistration() {
            return (clientSubscriptions, consumer) -> clientSubscriptions.isRequested(OperationType.TASK)
                ? Collections.singletonList(new JavaCompileTaskSuccessResultPostProcessor())
                : emptyList();
        }
    }

    private static class JavaBuildScopeServices implements ServiceRegistrationProvider {
        @Provides
        @SuppressWarnings("UnusedVariable") //registration
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary.class)
                .registerArtifactType(JavadocArtifact.class, ArtifactType.JAVADOC);
        }
    }

    private static class JavaProjectScopeServices implements ServiceRegistrationProvider {
        @Provides
        public ToolchainToolFactory createToolFactory(ExecActionFactory generator) {
            // TODO should we create all tools via this factory?
            return new ToolchainToolFactory() {
                @Override
                public <T> T create(Class<T> toolType, JavaToolchain toolchain) {
                    if (toolType == JavadocTool.class) {
                        return toolType.cast(new JavadocToolAdapter(generator, toolchain));
                    }
                    return null;
                }
            };
        }
    }
}
