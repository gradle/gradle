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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.AbstractModuleExclusion.isWildcard;

/**
 * Manages sets of exclude rules, allowing union and intersection operations on the rules.
 *
 * <p>This class attempts to reduce execution time, by flattening union and intersection specs, at the cost of more analysis at construction time. This is taken advantage of by {@link
 * org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder}, on the assumption that there are many more edges in the dependency graph than there are exclude rules (ie
 * we evaluate the rules much more often that we construct them). </p>
 *
 * <p>Also, this class attempts to be quite accurate in determining if 2 specs will exclude exactly the same set of modules. {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder}
 * uses this to avoid traversing the dependency graph of a particular version that has already been traversed when a new incoming edge is added (eg a newly discovered dependency) and when an incoming
 * edge is removed (eg a conflict evicts a version that depends on the given version). </p>
 *
 * <ul> <li>When a module dependency has multiple exclusions, then the resulting exclusion is the _intersection_ of those exclusions (module is excluded if excluded by _any_).</li> <li>When a module
 * is depended on via a transitive path, then the resulting exclusion is the _intersection_ of the exclusions on each leg of the path (module is excluded if excluded by _any_).</li> <li>When a module
 * is depended on via multiple paths in the graph, then the resulting exclusion is the _union_ of the exclusions on each of those paths (module is excluded if excluded by _all_).</li> </ul>
 */
public class ModuleExclusions {
    private static final ExcludeNone EXCLUDE_NONE = new ExcludeNone();
    private static final ExcludeAllModulesSpec EXCLUDE_ALL_MODULES_SPEC = new ExcludeAllModulesSpec();

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    private final Map<List<Exclude>, Map<Set<String>, ModuleExclusion>> cachedExcludes = Maps.newConcurrentMap();
    private final Map<MergeOperation, AbstractModuleExclusion> mergeCache = Maps.newConcurrentMap();
    private final Map<List<Exclude>, AbstractModuleExclusion> excludeAnyCache = Maps.newConcurrentMap();
    private final Map<Set<AbstractModuleExclusion>, ImmutableModuleExclusionSet> exclusionSetCache = Maps.newConcurrentMap();

    public ModuleExclusions(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    public ModuleExclusion excludeAny(List<Exclude> excludes, Set<String> hierarchy) {
        Map<Set<String>, ModuleExclusion> exclusionMap = cachedExcludes.get(excludes);
        if (exclusionMap == null) {
            exclusionMap = Maps.newConcurrentMap();
            cachedExcludes.put(excludes, exclusionMap);
        }
        ModuleExclusion moduleExclusion = exclusionMap.get(hierarchy);
        if (moduleExclusion == null) {
            List<Exclude> filtered = Lists.newArrayList();
            for (Exclude exclude : excludes) {
                for (String config : exclude.getConfigurations()) {
                    if (hierarchy.contains(config)) {
                        filtered.add(exclude);
                        break;
                    }
                }
            }
            moduleExclusion = excludeAny(filtered);
            exclusionMap.put(hierarchy, moduleExclusion);
        }
        return moduleExclusion;
    }

    private ImmutableModuleExclusionSet asImmutable(Set<AbstractModuleExclusion> excludes) {
        ImmutableModuleExclusionSet cached = exclusionSetCache.get(excludes);
        if (cached == null) {
            cached = new ImmutableModuleExclusionSet(excludes);
            exclusionSetCache.put(excludes, cached);
        }
        return cached;
    }

    /**
     * Returns a spec that excludes nothing.
     */
    public static ModuleExclusion excludeNone() {
        return EXCLUDE_NONE;
    }

    /**
     * Returns a spec that excludes those modules and artifacts that are excluded by _any_ of the given exclude rules.
     */
    public ModuleExclusion excludeAny(Exclude... excludes) {
        if (excludes.length == 0) {
            return EXCLUDE_NONE;
        }
        return excludeAny(Arrays.asList(excludes));
    }

    /**
     * Returns a spec that excludes those modules and artifacts that are excluded by _any_ of the given exclude rules.
     */
    public ModuleExclusion excludeAny(List<Exclude> excludes) {
        if (excludes.isEmpty()) {
            return EXCLUDE_NONE;
        }
        AbstractModuleExclusion exclusion = excludeAnyCache.get(excludes);
        if (exclusion != null) {
            return exclusion;
        }
        Set<AbstractModuleExclusion> exclusions = Sets.newHashSetWithExpectedSize(excludes.size());
        for (Exclude exclude : excludes) {
            exclusions.add(forExclude(exclude));
        }
        exclusion = new IntersectionExclusion(asImmutable(exclusions));
        excludeAnyCache.put(excludes, exclusion);
        return exclusion;
    }

    private static AbstractModuleExclusion forExclude(Exclude rule) {
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
                return new ModuleIdExcludeSpec(moduleId);
            } else if (!anyModule) {
                return new ModuleNameExcludeSpec(moduleId.getName());
            } else if (!anyOrganisation) {
                return new GroupNameExcludeSpec(moduleId.getGroup());
            } else {
                return EXCLUDE_ALL_MODULES_SPEC;
            }
        } else {
            return new ArtifactExcludeSpec(moduleId, artifact);
        }
    }

    /**
     * Returns a spec that excludes those modules and artifacts that are excluded by _either_ of the given exclude rules.
     */
    public ModuleExclusion intersect(ModuleExclusion one, ModuleExclusion two) {
        if (one == two) {
            return one;
        }
        if (one == EXCLUDE_NONE) {
            return two;
        }
        if (two == EXCLUDE_NONE) {
            return one;
        }
        if (one.equals(two)) {
            return one;
        }

        if (one instanceof IntersectionExclusion && ((IntersectionExclusion) one).getFilters().contains(two)) {
            return one;
        } else if (two instanceof IntersectionExclusion && ((IntersectionExclusion) two).getFilters().contains(one)) {
            return two;
        }

        Set<AbstractModuleExclusion> builder = Sets.newHashSet();

        ((AbstractModuleExclusion) one).unpackIntersection(builder);
        ((AbstractModuleExclusion) two).unpackIntersection(builder);

        return new IntersectionExclusion(asImmutable(builder));
    }

    /**
     * Returns a spec that excludes only those modules and artifacts that are excluded by _both_ of the supplied exclude rules.
     */
    public ModuleExclusion union(ModuleExclusion one, ModuleExclusion two) {
        if (one == two) {
            return one;
        }
        if (one == EXCLUDE_NONE || two == EXCLUDE_NONE) {
            return EXCLUDE_NONE;
        }
        if (one.equals(two)) {
            return one;
        }

        List<AbstractModuleExclusion> specs = new ArrayList<AbstractModuleExclusion>();
        ((AbstractModuleExclusion) one).unpackUnion(specs);
        ((AbstractModuleExclusion) two).unpackUnion(specs);
        for (int i = 0; i < specs.size(); ) {
            AbstractModuleExclusion spec = specs.get(i);
            AbstractModuleExclusion merged = null;
            // See if we can merge any of the following specs into one
            for (int j = i + 1; j < specs.size(); j++) {
                merged = maybeMergeIntoUnion(spec, specs.get(j));
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
        return new UnionExclusion(specs);
    }

    /**
     * Attempt to merge 2 exclusions into a single filter that is the union of both.
     * Currently this is only implemented when both exclusions are `IntersectionExclusion`s.
     */
    private AbstractModuleExclusion maybeMergeIntoUnion(ModuleExclusion one, ModuleExclusion two) {
        if (one instanceof IntersectionExclusion && two instanceof IntersectionExclusion) {
            return maybeMergeIntoUnion((IntersectionExclusion) one, (IntersectionExclusion) two);
        }
        return null;
    }

    private AbstractModuleExclusion maybeMergeIntoUnion(IntersectionExclusion one, IntersectionExclusion other) {
        Collection<AbstractModuleExclusion> oneFilters = one.getFilters();
        Collection<AbstractModuleExclusion> otherFilters = other.getFilters();
        if (oneFilters.equals(otherFilters)) {
            return one;
        }

        // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
        for (AbstractModuleExclusion excludeSpec : oneFilters) {
            if (!canMerge(excludeSpec)) {
                return null;
            }
        }
        for (AbstractModuleExclusion excludeSpec : otherFilters) {
            if (!canMerge(excludeSpec)) {
                return null;
            }
        }

        MergeOperation merge = new MergeOperation(oneFilters, otherFilters);
        AbstractModuleExclusion exclusion = mergeCache.get(merge);
        if (exclusion != null) {
            return exclusion;
        }
        return mergeAndCacheResult(merge, oneFilters, otherFilters);

    }

    private AbstractModuleExclusion mergeAndCacheResult(MergeOperation merge, Collection<AbstractModuleExclusion> oneFilters, Collection<AbstractModuleExclusion> otherFilters) {
        AbstractModuleExclusion exclusion;// Merge the exclude rules from both specs into a single union spec.
        Set<AbstractModuleExclusion> merged = Sets.newHashSetWithExpectedSize(oneFilters.size() + otherFilters.size());
        for (AbstractModuleExclusion thisSpec : oneFilters) {
            for (AbstractModuleExclusion otherSpec : otherFilters) {
                mergeExcludeRules(thisSpec, otherSpec, merged);
            }
        }
        if (merged.isEmpty()) {
            exclusion = ModuleExclusions.EXCLUDE_NONE;
        } else {
            exclusion = new IntersectionExclusion(asImmutable(merged));
        }
        mergeCache.put(merge, exclusion);
        return exclusion;
    }

    private static boolean canMerge(AbstractModuleExclusion excludeSpec) {
        return excludeSpec instanceof ExcludeAllModulesSpec
            || excludeSpec instanceof ArtifactExcludeSpec
            || excludeSpec instanceof GroupNameExcludeSpec
            || excludeSpec instanceof ModuleNameExcludeSpec
            || excludeSpec instanceof ModuleIdExcludeSpec;
    }

    // Add exclusions to the list that will exclude modules/artifacts that are excluded by _both_ of the candidate rules.
    private void mergeExcludeRules(AbstractModuleExclusion spec1, AbstractModuleExclusion spec2, Set<AbstractModuleExclusion> merged) {
        if (spec1 == spec2) {
            merged.add(spec1);
        } else if (spec1 instanceof ExcludeAllModulesSpec) {
            // spec1 excludes everything: use spec2 excludes
            merged.add(spec2);
        } else if (spec2 instanceof ExcludeAllModulesSpec) {
            // spec2 excludes everything: use spec1 excludes
            merged.add(spec1);
        } else if (spec1 instanceof ArtifactExcludeSpec) {
            // Excludes _no_ modules, may exclude some artifacts.
            // This isn't right: We are losing the artifacts excluded by spec2
            // (2 artifact excludes should cancel out unless equal)
            merged.add(spec1);
        } else if (spec2 instanceof ArtifactExcludeSpec) {
            // Excludes _no_ modules, may exclude some artifacts.
            // This isn't right: We are losing the artifacts excluded by spec2
            merged.add(spec2);
        } else if (spec1 instanceof GroupNameExcludeSpec) {
            // Merge into a single exclusion for Group + Module
            mergeExcludeRules((GroupNameExcludeSpec) spec1, spec2, merged);
        } else if (spec2 instanceof GroupNameExcludeSpec) {
            // Merge into a single exclusion for Group + Module
            mergeExcludeRules((GroupNameExcludeSpec) spec2, spec1, merged);
        } else if (spec1 instanceof ModuleNameExcludeSpec) {
            // Merge into a single exclusion for Group + Module
            mergeExcludeRules((ModuleNameExcludeSpec) spec1, spec2, merged);
        } else if (spec2 instanceof ModuleNameExcludeSpec) {
            // Merge into a single exclusion for Group + Module
            mergeExcludeRules((ModuleNameExcludeSpec) spec2, spec1, merged);
        } else if ((spec1 instanceof ModuleIdExcludeSpec) && (spec2 instanceof ModuleIdExcludeSpec)) {
            // Excludes nothing if the excluded module ids don't match: in that case this rule contributes nothing to the union
            ModuleIdExcludeSpec moduleSpec1 = (ModuleIdExcludeSpec) spec1;
            ModuleIdExcludeSpec moduleSpec2 = (ModuleIdExcludeSpec) spec2;
            if (moduleSpec1.moduleId.equals(moduleSpec2.moduleId)) {
                merged.add(moduleSpec1);
            }
        } else {
            throw new UnsupportedOperationException(String.format("Cannot calculate intersection of exclude rules: %s, %s", spec1, spec2));
        }
    }

    private void mergeExcludeRules(GroupNameExcludeSpec spec1, AbstractModuleExclusion spec2, Set<AbstractModuleExclusion> merged) {
        if (spec2 instanceof GroupNameExcludeSpec) {
            // Intersection of 2 group excludes does nothing unless excluded groups match
            GroupNameExcludeSpec groupNameExcludeSpec = (GroupNameExcludeSpec) spec2;
            if (spec1.group.equals(groupNameExcludeSpec.group)) {
                merged.add(spec1);
            }
        } else if (spec2 instanceof ModuleNameExcludeSpec) {
            // Intersection of group & module name exclude only excludes module with matching group + name
            ModuleNameExcludeSpec moduleNameExcludeSpec = (ModuleNameExcludeSpec) spec2;
            merged.add(new ModuleIdExcludeSpec(moduleIdentifierFactory.module(spec1.group, moduleNameExcludeSpec.module)));
        } else if (spec2 instanceof ModuleIdExcludeSpec) {
            // Intersection of group + module id exclude only excludes the module id if the excluded groups match
            ModuleIdExcludeSpec moduleIdExcludeSpec = (ModuleIdExcludeSpec) spec2;
            if (moduleIdExcludeSpec.moduleId.getGroup().equals(spec1.group)) {
                merged.add(spec2);
            }
        } else {
            throw new UnsupportedOperationException(String.format("Cannot calculate intersection of exclude rules: %s, %s", spec1, spec2));
        }
    }

    private static void mergeExcludeRules(ModuleNameExcludeSpec spec1, AbstractModuleExclusion spec2, Set<AbstractModuleExclusion> merged) {
        if (spec2 instanceof ModuleNameExcludeSpec) {
            // Intersection of 2 module name excludes does nothing unless excluded module names match
            ModuleNameExcludeSpec moduleNameExcludeSpec = (ModuleNameExcludeSpec) spec2;
            if (spec1.module.equals(moduleNameExcludeSpec.module)) {
                merged.add(spec1);
            }
        } else if (spec2 instanceof ModuleIdExcludeSpec) {
            // Intersection of module name & module id exclude only excludes module if the excluded module names match
            ModuleIdExcludeSpec moduleIdExcludeSpec = (ModuleIdExcludeSpec) spec2;
            if (moduleIdExcludeSpec.moduleId.getName().equals(spec1.module)) {
                merged.add(spec2);
            }
        } else {
            throw new UnsupportedOperationException(String.format("Cannot calculate intersection of exclude rules: %s, %s", spec1, spec2));
        }
    }

    private static final class MergeOperation {
        private final Collection<AbstractModuleExclusion> one;
        private final Collection<AbstractModuleExclusion> two;
        private final int hashCode;


        private MergeOperation(Collection<AbstractModuleExclusion> one, Collection<AbstractModuleExclusion> two) {
            this.one = one;
            this.two = two;
            this.hashCode = 31 * one.hashCode() + two.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MergeOperation that = (MergeOperation) o;

            if (!one.equals(that.one)) {
                return false;
            }
            return two.equals(that.two);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private final static class ImmutableModuleExclusionSet implements Set<AbstractModuleExclusion> {
        private final Set<AbstractModuleExclusion> delegate;
        private final int hashCode;

        private ImmutableModuleExclusionSet(Set<AbstractModuleExclusion> delegate) {
            this.delegate = delegate;
            this.hashCode = delegate.hashCode();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<AbstractModuleExclusion> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(AbstractModuleExclusion abstractModuleExclusion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends AbstractModuleExclusion> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ImmutableModuleExclusionSet that = (ImmutableModuleExclusionSet) o;

            if (hashCode != that.hashCode) {
                return false;
            }

            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
