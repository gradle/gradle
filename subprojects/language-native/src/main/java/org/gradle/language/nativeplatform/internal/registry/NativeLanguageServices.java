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

package org.gradle.language.nativeplatform.internal.registry;

import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.language.cpp.internal.NativeDependencyCache;
import org.gradle.language.nativeplatform.internal.incremental.DefaultCompilationStateCacheFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultIncrementalCompilerBuilder;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CachingCSourceParser;
import org.gradle.language.swift.internal.SwiftStdlibToolLocator;

public class NativeLanguageServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.add(DefaultCompilationStateCacheFactory.class);
        registration.add(CachingCSourceParser.class);
        registration.add(DefaultIncrementalCompilerBuilder.class);
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.add(SwiftStdlibToolLocator.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(NativeDependencyCache.class);
    }
}
