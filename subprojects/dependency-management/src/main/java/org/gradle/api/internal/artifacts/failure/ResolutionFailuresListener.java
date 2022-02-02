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
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.IdentityHashMap;
import java.util.Map;

@ThreadSafe
@ServiceScope(Scopes.BuildTree.class)
public class ResolutionFailuresListener implements DependencyResolutionListener {
    @GuardedBy("self") private final Map<ResolvableDependencies, Throwable> errors = new IdentityHashMap<>();

    private ThreadLocal<ResolvableDependencies> currentResolution = new ThreadLocal<>();

    @Override
    public void beforeResolve(ResolvableDependencies dependencies) {
        currentResolution.set(dependencies);
    }

    @Override
    public void afterResolve(ResolvableDependencies dependencies) {
        currentResolution = null;
    }

    public void logError(Throwable t) {
        synchronized (errors) {
            errors.put(currentResolution.get(), t);
        }
    }

    public Map<ResolvableDependencies, Throwable> getErrors() {
        synchronized (errors) {
            return ImmutableMap.copyOf(errors);
        }
    }
}
