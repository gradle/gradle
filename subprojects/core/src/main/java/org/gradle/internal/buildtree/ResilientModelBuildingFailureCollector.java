/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildtree;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects failures thrown by tooling model builders during resilient model building (after the target project
 * configured successfully). Unlike configuration failures, these would otherwise only be reported as per-model
 * failures; they are added to the build tree failures at build finish so the build fails while partial models are
 * still returned to the client.
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public class ResilientModelBuildingFailureCollector {

    private final List<Throwable> failures = new ArrayList<>();

    public synchronized void add(Throwable failure) {
        failures.add(failure);
    }

    /**
     * Returns the collected failures and clears them, so they are not reported again if the build tree is reused.
     */
    public synchronized List<Throwable> getAndClearFailures() {
        List<Throwable> collected = ImmutableList.copyOf(failures);
        failures.clear();
        return collected;
    }
}
