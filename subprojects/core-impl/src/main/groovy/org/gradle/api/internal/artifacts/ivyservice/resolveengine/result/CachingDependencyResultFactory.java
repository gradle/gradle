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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * by Szczepan Faber, created at: 10/1/12
 */
public class CachingDependencyResultFactory {

    Map<List, DefaultUnresolvedDependencyResult> unresolvedDependencies = new LinkedHashMap();
    Map<List, DefaultResolvedDependencyResult> resolvedDependencies = new LinkedHashMap();

    public DependencyResult createUnresolvedDependency(ModuleVersionSelector requested, ResolvedModuleVersionResult from, Exception failure) {
        List<Object> key = asList(requested, from);
        if (!unresolvedDependencies.containsKey(key)) {
            unresolvedDependencies.put(key, new DefaultUnresolvedDependencyResult(requested, failure, from));
        }
        return unresolvedDependencies.get(key);
    }

    public DependencyResult createResolvedDependency(ModuleVersionSelector requested, ResolvedModuleVersionResult from, ResolvedModuleVersionResult selected) {
        List<Object> key = asList(requested, from, selected);
        if (!resolvedDependencies.containsKey(key)) {
            resolvedDependencies.put(key, new DefaultResolvedDependencyResult(requested, selected, from));
        }
        return resolvedDependencies.get(key);
    }
}