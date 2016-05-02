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
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.AbstractModuleExcludeRuleFilter.isWildcard;

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
 *
 * <ul>
 *     <li>When a module dependency has multiple exclusions, then the resulting exclusion is the _intersection_ of those exclusions (module is excluded if excluded by _any_).</li>
 *     <li>When a module is depended on via a transitive path, then the resulting exclusion is the _intersection_ of the exclusions on each leg of the path (module is excluded if excluded by _any_).</li>
 *     <li>When a module is depended on via multiple paths in the graph, then the resulting exclusion is the _union_ of the exclusions on each of those paths (module is excluded if excluded by _all_).</li>
 * </ul>
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
        return excludeAny(Arrays.asList(excludeRules));
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return EXCLUDE_NONE;
        }
        return new MultipleExcludeRulesFilter(CollectionUtils.collect(excludeRules, new Transformer<AbstractModuleExcludeRuleFilter, ExcludeRule>() {
            @Override
            public AbstractModuleExcludeRuleFilter transform(ExcludeRule excludeRule) {
                return forIvyExcludeRule(excludeRule);
            }
        }));
    }

    private static AbstractModuleExcludeRuleFilter forIvyExcludeRule(ExcludeRule rule) {
        // For custom ivy pattern matchers, don't inspect the rule any more deeply: this prevents us from doing smart merging later
        if (!(rule.getMatcher() instanceof ExactPatternMatcher)) {
            return new IvyPatternMatcherExcludeRuleSpec(rule);
        }

        ArtifactId artifactId = rule.getId();
        ModuleId moduleId = artifactId.getModuleId();
        boolean anyOrganisation = isWildcard(moduleId.getOrganisation());
        boolean anyModule = isWildcard(moduleId.getName());
        boolean anyArtifact = isWildcard(artifactId.getName()) && isWildcard(artifactId.getType()) && isWildcard(artifactId.getExt());

        // Build a strongly typed (mergeable) exclude spec for each supplied rule
        if (anyArtifact) {
            if (!anyOrganisation && !anyModule) {
                return new ModuleIdExcludeSpec(moduleId.getOrganisation(), moduleId.getName());
            } else if (!anyModule) {
                return new ModuleNameExcludeSpec(moduleId.getName());
            } else if (!anyOrganisation) {
                return new GroupNameExcludeSpec(moduleId.getOrganisation());
            } else {
                return new ExcludeAllModulesSpec();
            }
        } else {
            return new ArtifactExcludeSpec(artifactId);
        }
    }

    /**
     * Returns a filter that accepts the union of those module versions and artifacts that are accepted by this filter and the other.
     * The union excludes only if _both_ of the input filters exclude.
     */
    public static ModuleExcludeRuleFilter union(ModuleExcludeRuleFilter one, ModuleExcludeRuleFilter two) {
        if (one == two) {
            return one;
        }
        if (one == EXCLUDE_NONE || two == EXCLUDE_NONE) {
            return EXCLUDE_NONE;
        }

        List<AbstractModuleExcludeRuleFilter> specs = new ArrayList<AbstractModuleExcludeRuleFilter>();
        ((AbstractModuleExcludeRuleFilter) one).unpackUnion(specs);
        ((AbstractModuleExcludeRuleFilter) two).unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            AbstractModuleExcludeRuleFilter spec = specs.get(i);
            AbstractModuleExcludeRuleFilter merged = null;
            // See if we can merge any of the following specs into one
            for (int j = i + 1; j < specs.size(); j++) {
                merged = spec.maybeMergeIntoUnion(specs.get(j));
                if (merged != null) {
                    specs.remove(j);
                    break;
                }
            }
            if (merged != null) {
                specs.set(i, merged);
            } else {
                i++;
            }
        }
        if (specs.size() == 1) {
            return specs.get(0);
        }
        return new UnionExcludeRuleFilter(specs);
    }

    /**
     * Returns a filter that accepts the intersection of those module versions and artifacts that are accepted by this filter and the other.
     * The intersection excludes if _either_ of the input filters exclude.
     */
    public static ModuleExcludeRuleFilter intersect(ModuleExcludeRuleFilter one, ModuleExcludeRuleFilter two) {
        if (one == two) {
            return one;
        }
        if (one == EXCLUDE_NONE) {
            return two;
        }
        if (two == EXCLUDE_NONE) {
            return one;
        }

        List<AbstractModuleExcludeRuleFilter> specs = new ArrayList<AbstractModuleExcludeRuleFilter>();
        ((AbstractModuleExcludeRuleFilter) one).unpackIntersection(specs);
        ((AbstractModuleExcludeRuleFilter) two).unpackIntersection(specs);

        return new MultipleExcludeRulesFilter(specs);
    }
}
