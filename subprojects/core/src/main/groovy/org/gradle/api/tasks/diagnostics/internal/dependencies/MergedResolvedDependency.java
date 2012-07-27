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

package org.gradle.api.tasks.diagnostics.internal.dependencies;

import org.gradle.api.artifacts.ResolvedDependency;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
* by Szczepan Faber, created at: 7/27/12
*/
public class MergedResolvedDependency implements RenderableDependency {
    private Set<ResolvedDependency> mergedResolvedDependencies = new LinkedHashSet<ResolvedDependency>();

    public MergedResolvedDependency(Set<ResolvedDependency> mergedResolvedDependencies) {
        assert !mergedResolvedDependencies.isEmpty();
        this.mergedResolvedDependencies = mergedResolvedDependencies;
    }

    public String getName() {
        return mergedResolvedDependencies.iterator().next().getName();
    }

    public String getConfiguration() {
        String mergedConfiguration = "";
        for (ResolvedDependency mergedResolvedDependency : mergedResolvedDependencies) {
            mergedConfiguration += mergedResolvedDependency.getConfiguration() + ",";
        }
        return mergedConfiguration.substring(0, mergedConfiguration.length() - 1);
    }

    public Set<RenderableDependency> getChildren() {
        Set<ResolvedDependency> mergedChildren = new LinkedHashSet<ResolvedDependency>();
        for (ResolvedDependency resolvedDependency : mergedResolvedDependencies) {
            mergedChildren.addAll(resolvedDependency.getChildren());
        }
        return mergeChildren(mergedChildren);
    }

    public static Set<RenderableDependency> mergeChildren(Set<ResolvedDependency> children) {
        Map<String, Set<ResolvedDependency>> mergedGroups = new LinkedHashMap<String, Set<ResolvedDependency>>();
        for (ResolvedDependency child : children) {
            Set<ResolvedDependency> mergeGroup = mergedGroups.get(child.getName());
            if (mergeGroup == null) {
                mergedGroups.put(child.getName(), mergeGroup = new LinkedHashSet<ResolvedDependency>());
            }
            mergeGroup.add(child);
        }
        Set<RenderableDependency> mergedChildren = new LinkedHashSet<RenderableDependency>();
        for (Set<ResolvedDependency> mergedGroup : mergedGroups.values()) {
            mergedChildren.add(new MergedResolvedDependency(mergedGroup));
        }
        return mergedChildren;
    }
}
