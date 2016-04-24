/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.apache.ivy.core.module.descriptor.ExcludeRule;

import java.util.Arrays;
import java.util.Collection;

/**
 * Manages sets of exclude rules, allowing union and intersection operations on the rules.
 *
 * <p>This class attempts to reduce execution time, by flattening union and intersection specs, at the cost of more analysis at construction time. This is taken advantage of by {@link
 * org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder}, on the assumption that there are many more edges in the dependency graph than there are exclude rules (ie we evaluate the rules much more often that we construct them).
 * </p>
 *
 * <p>Also, this class attempts to be quite accurate in determining if 2 specs will match exactly the same set of modules. {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder} uses this to avoid traversing the
 * dependency graph of a particular version that has already been traversed when a new incoming edge is added (eg a newly discovered dependency) and when an incoming edge is removed (eg a conflict
 * evicts a version that depends on the given version). </p>
 */
public class ModuleExcludeRuleFilters {
    static final ExcludeNone EXCLUDE_NONE = new ExcludeNone();

    /**
     * Returns a spec that accepts everything.
     */
    public static ModuleExcludeRuleFilter excludeNone() {
        return EXCLUDE_NONE;
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(ExcludeRule... excludeRules) {
        if (excludeRules.length == 0) {
            return EXCLUDE_NONE;
        }
        return new MultipleExcludeRulesFilter(Arrays.asList(excludeRules));
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return EXCLUDE_NONE;
        }
        return new MultipleExcludeRulesFilter(excludeRules);
    }
}
