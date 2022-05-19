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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.migration.ListPropertyWrapper;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.internal.tasks.compile.tooling.JavaCompileTaskSuccessResultPostProcessor;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.build.event.OperationResultPostProcessorFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.upgrade.ApiUpgradeManager;
import org.gradle.jvm.JvmLibrary;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.jvm.toolchain.internal.ToolchainToolFactory;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.tooling.events.OperationType;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            public AnnotationProcessorDetector createAnnotationProcessorDetector(FileContentCacheFactory cacheFactory, LoggingConfiguration loggingConfiguration) {
                return new AnnotationProcessorDetector(cacheFactory, LoggerFactory.getLogger(AnnotationProcessorDetector.class), loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS);
            }
        });
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new ProviderApiMigrationAction());
    }

    private static class JavaGlobalScopeServices {
        OperationResultPostProcessorFactory createJavaSubscribableBuildActionRunnerRegistration() {
            return (clientSubscriptions, consumer) -> clientSubscriptions.isRequested(OperationType.TASK)
                ? Collections.singletonList(new JavaCompileTaskSuccessResultPostProcessor())
                : emptyList();
        }
    }

    private static class ProviderApiMigrationAction {
        @SuppressWarnings("unchecked")
        public void configure(ApiUpgradeManager upgradeManager, FileCollectionFactory fileCollectionFactory) {
            // It seems like the bytecode references the subclass as an owner
            upgradeManager
                .matchProperty(AbstractCompile.class, String.class, "targetCompatibility", Collections.singletonList(JavaCompile.class.getName()))
                .replaceWith(
                    abstractCompile -> abstractCompile.getTargetCompatibility().get(),
                    (abstractCompile, value) -> abstractCompile.getTargetCompatibility().set(value)
                );
            upgradeManager
                .matchProperty(AbstractCompile.class, String.class, "sourceCompatibility", Collections.singletonList(JavaCompile.class.getName()))
                .replaceWith(
                    abstractCompile -> abstractCompile.getSourceCompatibility().get(),
                    (abstractCompile, value) -> abstractCompile.getSourceCompatibility().set(value)
                );
            upgradeManager
                .matchProperty(AbstractCompile.class, FileCollection.class, "classpath", ImmutableList.of(JavaCompile.class.getName(), "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile", "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask", "org.gradle.api.tasks.compile.GroovyCompile"))
                .replaceWith(
                    abstractCompile -> fileCollectionFactory
                        .resolving(new ArrayList<>(abstractCompile.getClasspath().getFrom())),
                    (abstractCompile, value) -> abstractCompile.getClasspath().setFrom(value)
                );
            upgradeManager
                .matchProperty(CompileOptions.class, List.class, "compilerArgs")
                .replaceWith(
                    options -> new ListPropertyWrapper<>(options.getCompilerArgs()),
                    (options, compilerArgs) -> options.getCompilerArgs().set(compilerArgs)
                );
            upgradeManager
                .matchProperty(Copy.class, File.class, "destinationDir", ImmutableList.of(ProcessResources.class.getName()))
                .replaceWith(
                    copy -> copy.getDestinationDir().get().getAsFile(),
                    (copy, destinationDir) -> copy.getDestinationDir().set(destinationDir)
                );
        }
    }

    private static class JavaGradleScopeServices {
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary.class)
                .registerArtifactType(JavadocArtifact.class, ArtifactType.JAVADOC);
        }
    }

    private static class JavaProjectScopeServices {

        public ToolchainToolFactory createToolFactory(ExecActionFactory generator) {
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
