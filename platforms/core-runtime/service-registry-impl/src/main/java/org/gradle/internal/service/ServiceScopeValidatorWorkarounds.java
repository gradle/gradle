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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ServiceScopeValidatorWorkarounds {

    private static final Set<String> SUPPRESSED_VALIDATION_CLASSES = new HashSet<String>(Arrays.asList(
        "com.google.common.collect.ImmutableList",
        "java.util.Properties",

        "org.gradle.internal.Factory",
        "org.gradle.internal.serialize.Serializer",
        "org.gradle.cache.internal.ProducerGuard",
        "org.gradle.internal.typeconversion.NotationParser",

        // Avoid annotating services published as public libraries
        // build-cache-base:
        "org.gradle.caching.internal.origin.OriginMetadataFactory",

        // It's supposed to be only in the Settings scope
        // However, ProjectBuilderImpl does not instantiate that scope at all, while still requiring the service
        // Because of this, it artificially puts it into the Build-scope to make it available
        "org.gradle.initialization.DefaultProjectDescriptorRegistry",

        // Problematic with GradleBuild task and CC, because marking it as a service
        // makes CC skip serialization and instead use service look-up which yield a wrong value for this specially setup task
        "org.gradle.api.internal.StartParameterInternal",

        // Configuration Cache service Codec fails to get an instance of this service, due to multiple being available
        "org.gradle.caching.configuration.internal.BuildCacheServiceRegistration",

        "org.gradle.nativeplatform.platform.internal.NativePlatforms",
        "org.gradle.nativeplatform.internal.NativePlatformResolver",
        "org.gradle.nativeplatform.internal.DefaultTargetMachineFactory",

        // Build init feature of converting Maven to Gradle build stops working with CC
        "org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry",

        // Non-trivial case with generics
        "org.gradle.internal.event.ListenerBroadcast"
    ));

    public static boolean shouldSuppressValidation(Class<?> serviceType) {
        return SUPPRESSED_VALIDATION_CLASSES.contains(serviceType.getName());
    }

}
