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
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.language.cpp.internal.NativeDependencyCache;
import org.gradle.language.internal.DefaultNativeComponentFactory;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultCompilationStateCacheFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultIncrementalCompilerBuilder;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CachingCSourceParser;
import org.gradle.language.nativeplatform.internal.toolchains.DefaultToolChainSelector;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;

public class NativeLanguageServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(NativeDependencyCache.class);
        registration.add(DefaultCompilationStateCacheFactory.class);
        registration.add(CSourceParser.class, CachingCSourceParser.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(IncrementalCompilerBuilder.class, DefaultIncrementalCompilerBuilder.class);
        registration.add(ToolChainSelector.class, DefaultToolChainSelector.class);
        registration.add(NativeComponentFactory.class, DefaultNativeComponentFactory.class);
    }
}
