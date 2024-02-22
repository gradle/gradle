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

package org.gradle.api.internal.initialization;

import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.provider.Provider;

import java.util.function.Function;

public class ScriptClassPathResolutionContext {
    private final Provider<CacheInstrumentationDataBuildService> buildService;
    private final DependencyHandler dependencyHandler;

    public ScriptClassPathResolutionContext(
        Provider<CacheInstrumentationDataBuildService> buildService,
        DependencyHandler dependencyHandler
    ) {
        this.buildService = buildService;
        this.dependencyHandler = dependencyHandler;
    }

    public DependencyHandler getDependencyHandler() {
        return dependencyHandler;
    }

    public <T> T runAndClearCachedDataAfter(Function<CacheInstrumentationDataBuildService, T> resolutionFunction) {
        CacheInstrumentationDataBuildService buildService = this.buildService.get();
        T result = resolutionFunction.apply(buildService);
        buildService.clear();
        return result;
    }
}
