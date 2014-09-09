/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class CachingDependencyResultFactory {

    private final Map<List, DefaultUnresolvedDependencyResult> unresolvedDependencies = new HashMap<List, DefaultUnresolvedDependencyResult>();
    private final Map<List, DefaultResolvedDependencyResult> resolvedDependencies = new HashMap<List, DefaultResolvedDependencyResult>();

    public UnresolvedDependencyResult createUnresolvedDependency(ComponentSelector requested, ResolvedComponentResult from,
                                                       ComponentSelectionReason reason, ModuleVersionResolveException failure) {
        List<Object> key = asList(requested, from);
        if (!unresolvedDependencies.containsKey(key)) {
            unresolvedDependencies.put(key, new DefaultUnresolvedDependencyResult(requested, reason, from, failure));
        }
        return unresolvedDependencies.get(key);
    }

    public ResolvedDependencyResult createResolvedDependency(ComponentSelector requested, ResolvedComponentResult from, DefaultResolvedComponentResult selected) {
        List<Object> key = asList(requested, from, selected);
        if (!resolvedDependencies.containsKey(key)) {
            resolvedDependencies.put(key, new DefaultResolvedDependencyResult(requested, selected, from));
        }
        return resolvedDependencies.get(key);
    }
}