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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
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
     * Returns a spec that excludes nothing.
     */
    public static ModuleExcludeRuleFilter excludeNone() {
        return EXCLUDE_NONE;
    }

    /**
     * Returns a spec that excludes those modules and artifacts that are excluded by _any_ of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(Exclude... excludes) {
        if (excludes.length == 0) {
            return EXCLUDE_NONE;
        }
        return excludeAny(Arrays.asList(excludes));
    }

    /**
     * Returns a spec that excludes those modules and artifacts that are excluded by _any_ of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(Collection<Exclude> excludes) {
        if (excludes.isEmpty()) {
            return EXCLUDE_NONE;
        }
        return new MultipleExcludeRulesFilter(CollectionUtils.collect(excludes, new Transformer<AbstractModuleExcludeRuleFilter, Exclude>() {
            @Override
            public AbstractModuleExcludeRuleFilter transform(Exclude exclude) {
                return forExclude(exclude);
            }
        }));
    }

    private static AbstractModuleExcludeRuleFilter forExclude(Exclude rule) {
        // For custom ivy pattern matchers, don't inspect the rule any more deeply: this prevents us from doing smart merging later
        if (!PatternMatchers.isExactMatcher(rule.getMatcher())) {
            return new IvyPatternMatcherExcludeRuleSpec(rule);
        }

        ModuleIdentifier moduleId = rule.getModuleId();
        IvyArtifactName artifact = rule.getArtifact();
        boolean anyOrganisation = isWildcard(moduleId.getGroup());
        boolean anyModule = isWildcard(moduleId.getName());
        boolean anyArtifact = isWildcard(artifact.getName()) && isWildcard(artifact.getType()) && isWildcard(artifact.getExtension());

        // Build a strongly typed (mergeable) exclude spec for each supplied rule
        if (anyArtifact) {
            if (!anyOrganisation && !anyModule) {
                return new ModuleIdExcludeSpec(moduleId.getGroup(), moduleId.getName());
            } else if (!anyModule) {
                return new ModuleNameExcludeSpec(moduleId.getName());
            } else if (!anyOrganisation) {
                return new GroupNameExcludeSpec(moduleId.getGroup());
            } else {
                return new ExcludeAllModulesSpec();
            }
        } else {
            return new ArtifactExcludeSpec(moduleId, artifact);
        }
    }

    /**
     * Returns a spec that excludes only those modules and artifacts that are excluded by both of the supplied exclude rules.
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
     * Returns a spec that excludes those modules and artifacts that are excluded by _either_ of the given exclude rules.
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
