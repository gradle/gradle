/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.failure;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.HashMap;
import java.util.Map;

/**
 * A listener that logs dependencies which experience resolution failures.
 *
 * Thread-safe, in case multiple threads are resolving dependencies simultaneously.
 *
 * @since 7.5
 */
@ServiceScope(Scopes.BuildTree.class)
public class ResolutionFailuresListener implements DependencyResolutionListener {
    private final Map<String, Throwable> errors = new HashMap<>();
    private ResolvableDependencies currentResolution;

    @Override
    public void beforeResolve(ResolvableDependencies dependencies) {
        currentResolution = dependencies;
    }

    @Override
    public void afterResolve(ResolvableDependencies dependencies) {
        currentResolution = null;
    }

    public void logError(Throwable t) {
        final String key;
        if (t instanceof ModuleVersionNotFoundException) {
            ModuleVersionNotFoundException mvnfe = (ModuleVersionNotFoundException) t;
            key = mvnfe.getSelector().getDisplayName();
        } else {
            key = currentResolution.getName();
        }

        if (!errors.containsKey(key)) {
            errors.put(key, t);
        }
    }

    public Map<String, Throwable> getErrors() {
        return ImmutableMap.copyOf(errors);
    }
}
