/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.service;

import org.gradle.api.NonNullApi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@NonNullApi
public class ServiceScopeValidatorWorkarounds {

    private static final Set<String> SUPPRESSED_VALIDATION_CLASSES = new HashSet<String>(Arrays.asList(
        "com.google.common.collect.ImmutableList",
        "java.util.Properties",

        "org.gradle.internal.Factory",
        "org.gradle.internal.serialize.Serializer",
        "org.gradle.execution.DefaultWorkValidationWarningRecorder",
        "org.gradle.api.internal.classpath.DefaultModuleRegistry",
        "org.gradle.tooling.internal.provider.runner.ToolingApiBuildEventListenerFactory",
        "org.gradle.cache.internal.ProducerGuard",
        "org.gradle.internal.typeconversion.NotationParser",

        "org.gradle.api.internal.artifacts.transform.InputArtifactDependenciesAnnotationHandler",
        "org.gradle.api.internal.artifacts.transform.InputArtifactAnnotationHandler",

        "org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler",
        "org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler",
        "org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler",
        "org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler",

        "org.gradle.api.reporting.components.internal.TypeAwareBinaryRenderer",

        "org.gradle.nativeplatform.internal.NativeBinaryRenderer",
        "org.gradle.nativeplatform.internal.SharedLibraryBinaryRenderer",
        "org.gradle.nativeplatform.internal.StaticLibraryBinaryRenderer",
        "org.gradle.nativeplatform.internal.NativeExecutableBinaryRenderer",
        "org.gradle.nativeplatform.platform.internal.NativePlatforms",
        "org.gradle.nativeplatform.internal.NativePlatformResolver",
        "org.gradle.nativeplatform.internal.DefaultTargetMachineFactory",
        "org.gradle.nativeplatform.test.internal.NativeTestSuiteBinaryRenderer",

        "org.gradle.internal.scripts.DefaultScriptFileResolverListeners",

        "org.gradle.plugin.software.internal.SoftwareTypeAnnotationHandler"
    ));

    public static boolean shouldSuppressValidation(Class<?> serviceType) {
        return SUPPRESSED_VALIDATION_CLASSES.contains(serviceType.getName());
    }

}
