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
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.*;

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
public abstract class DefaultModuleExcludeRuleFilter implements ModuleExcludeRuleFilter {
    private static final AcceptAllSpec ALL_SPEC = new AcceptAllSpec();
    private static final String WILDCARD = "*";

    /**
     * Returns a spec that accepts everything.
     */
    public static ModuleExcludeRuleFilter all() {
        return ALL_SPEC;
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(ExcludeRule... excludeRules) {
        if (excludeRules.length == 0) {
            return ALL_SPEC;
        }
        return new MultipleExcludeRulesSpec(Arrays.asList(excludeRules));
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleExcludeRuleFilter excludeAny(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return ALL_SPEC;
        }
        return new MultipleExcludeRulesSpec(excludeRules);
    }

    private static boolean isWildcard(String attribute) {
        return WILDCARD.equals(attribute);
    }

    public ModuleExcludeRuleFilter union(ModuleExcludeRuleFilter other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return other;
        }
        if (this == ALL_SPEC) {
            return this;
        }
        List<DefaultModuleExcludeRuleFilter> specs = new ArrayList<DefaultModuleExcludeRuleFilter>();
        unpackUnion(specs);
        ((DefaultModuleExcludeRuleFilter) other).unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            DefaultModuleExcludeRuleFilter spec = specs.get(i);
            DefaultModuleExcludeRuleFilter merged = null;
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
        return new UnionSpec(specs);
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as a union.
     */
    protected void unpackUnion(Collection<DefaultModuleExcludeRuleFilter> specs) {
        specs.add(this);
    }

    /**
     * Returns the union of this filter and the given filter. Returns null if not recognized.
     */
    protected DefaultModuleExcludeRuleFilter maybeMergeIntoUnion(DefaultModuleExcludeRuleFilter other) {
        return null;
    }

    public final boolean excludesSameModulesAs(ModuleExcludeRuleFilter filter) {
        if (filter == this) {
            return true;
        }
        DefaultModuleExcludeRuleFilter other = (DefaultModuleExcludeRuleFilter) filter;
        boolean thisAcceptsEverything = acceptsAllModules();
        boolean otherAcceptsEverything = other.acceptsAllModules();
        if (thisAcceptsEverything && otherAcceptsEverything) {
            return true;
        }
        if (thisAcceptsEverything ^ otherAcceptsEverything) {
            return false;
        }
        if (!other.getClass().equals(getClass())) {
            return false;
        }
        return doAcceptsSameModulesAs(other);
    }

    /**
     * Only called when this and the other spec have the same class.
     */
    protected boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
        return false;
    }

    protected boolean acceptsAllModules() {
        return false;
    }

    /**
     * Returns a spec that accepts the intersection of those module versions that are accepted by this spec and the given spec.
     */
    public ModuleExcludeRuleFilter intersect(ModuleExcludeRuleFilter other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return this;
        }
        if (this == ALL_SPEC) {
            return other;
        }

        List<DefaultModuleExcludeRuleFilter> specs = new ArrayList<DefaultModuleExcludeRuleFilter>();
        unpackIntersection(specs);
        ((DefaultModuleExcludeRuleFilter) other).unpackIntersection(specs);

        return new MultipleExcludeRulesSpec(specs);
    }

    /**
     * Possibly unpack a composite spec into it's constituent parts, if those parts are applied as an intersection.
     */
    protected void unpackIntersection(Collection<DefaultModuleExcludeRuleFilter> specs) {
        specs.add(this);
    }

    private static class AcceptAllSpec extends DefaultModuleExcludeRuleFilter {
        @Override
        public String toString() {
            return "{accept-all}";
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return true;
        }

        @Override
        protected boolean acceptsAllModules() {
            return true;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }

        @Override
        public boolean acceptsAllArtifacts() {
            return true;
        }
    }

    private static abstract class CompositeSpec extends DefaultModuleExcludeRuleFilter {
        abstract Collection<DefaultModuleExcludeRuleFilter> getSpecs();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append(getClass().getSimpleName());
            for (DefaultModuleExcludeRuleFilter spec : getSpecs()) {
                builder.append(' ');
                builder.append(spec);
            }
            builder.append("}");
            return builder.toString();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            CompositeSpec spec = (CompositeSpec) other;
            return implies(spec) && spec.implies(this);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            CompositeSpec other = (CompositeSpec) obj;
            return getSpecs().equals(other.getSpecs());
        }

        @Override
        public int hashCode() {
            return getSpecs().hashCode();
        }

        /**
         * Returns true if for every spec in this spec, there is a corresponding spec in the given spec that acceptsSameModulesAs().
         */
        protected boolean implies(CompositeSpec spec) {
            for (DefaultModuleExcludeRuleFilter thisSpec : getSpecs()) {
                boolean found = false;
                for (DefaultModuleExcludeRuleFilter otherSpec : spec.getSpecs()) {
                    if (thisSpec.excludesSameModulesAs(otherSpec)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A spec that selects only those artifacts and modules that satisfy _all_ of the supplied exclude rules.
     * As such, this is an intersection of the separate exclude rule filters.
     */
    private static class MultipleExcludeRulesSpec extends CompositeSpec {
        private final Set<DefaultModuleExcludeRuleFilter> excludeSpecs = new HashSet<DefaultModuleExcludeRuleFilter>();

        private MultipleExcludeRulesSpec(Iterable<ExcludeRule> excludeRules) {
            for (ExcludeRule rule : excludeRules) {

                // For custom ivy pattern matchers, don't inspect the rule any more deeply: this prevents us from doing smart merging later
                if (!(rule.getMatcher() instanceof ExactPatternMatcher)) {
                    excludeSpecs.add(new IvyPatternMatcherExcludeRuleSpec(rule));
                    continue;
                }

                ArtifactId artifactId = rule.getId();
                ModuleId moduleId = artifactId.getModuleId();
                boolean anyOrganisation = isWildcard(moduleId.getOrganisation());
                boolean anyModule = isWildcard(moduleId.getName());
                boolean anyArtifact = isWildcard(artifactId.getName()) && isWildcard(artifactId.getType()) && isWildcard(artifactId.getExt());

                // Build a strongly typed (mergeable) exclude spec for each supplied rule
                if (anyArtifact) {
                    if (!anyOrganisation && !anyModule) {
                        excludeSpecs.add(new ModuleIdExcludeSpec(moduleId.getOrganisation(), moduleId.getName()));
                    } else if (!anyModule) {
                        excludeSpecs.add(new ModuleNameExcludeSpec(moduleId.getName()));
                    } else if (!anyOrganisation) {
                        excludeSpecs.add(new GroupNameExcludeSpec(moduleId.getOrganisation()));
                    } else {
                        excludeSpecs.add(new ExcludeAllModulesSpec());
                    }
                } else {
                    excludeSpecs.add(new ArtifactExcludeSpec(artifactId));
                }
            }
        }

        public MultipleExcludeRulesSpec(Collection<DefaultModuleExcludeRuleFilter> specs) {
            this.excludeSpecs.addAll(specs);
        }

        @Override
        Collection<DefaultModuleExcludeRuleFilter> getSpecs() {
            return excludeSpecs;
        }

        @Override
        protected boolean acceptsAllModules() {
            for (DefaultModuleExcludeRuleFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptsAllModules()) {
                    return false;
                }
            }
            return true;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            for (DefaultModuleExcludeRuleFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptModule(element)) {
                    return false;
                }
            }
            return true;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            for (DefaultModuleExcludeRuleFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptArtifact(module, artifact)) {
                    return false;
                }
            }
            return true;
        }

        public boolean acceptsAllArtifacts() {
            for (DefaultModuleExcludeRuleFilter spec : excludeSpecs) {
                if (!spec.acceptsAllArtifacts()) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Can unpack into constituents when creating a larger intersection (since elements are applied as an intersection).
         */
        @Override
        protected void unpackIntersection(Collection<DefaultModuleExcludeRuleFilter> specs) {
            specs.addAll(excludeSpecs);
        }

        /**
         * Construct a filter that will accept any module/artifact that is accepted by this _or_ the other filter.
         * Returns null when this union cannot be computed.
         */
        @Override
        protected DefaultModuleExcludeRuleFilter maybeMergeIntoUnion(DefaultModuleExcludeRuleFilter other) {
            if (!(other instanceof MultipleExcludeRulesSpec)) {
                return null;
            }

            MultipleExcludeRulesSpec multipleExcludeRulesSpec = (MultipleExcludeRulesSpec) other;
            if (excludeSpecs.equals(multipleExcludeRulesSpec.excludeSpecs)) {
                return this;
            }

            // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
            for (DefaultModuleExcludeRuleFilter excludeSpec : excludeSpecs) {
                if (!canMerge(excludeSpec)) {
                    return null;
                }
            }
            for (DefaultModuleExcludeRuleFilter excludeSpec : multipleExcludeRulesSpec.excludeSpecs) {
                if (!canMerge(excludeSpec)) {
                    return null;
                }
            }

            // Merge the exclude rules from both specs into a single union spec.
            List<DefaultModuleExcludeRuleFilter> merged = new ArrayList<DefaultModuleExcludeRuleFilter>();
            for (DefaultModuleExcludeRuleFilter thisSpec : excludeSpecs) {
                for (DefaultModuleExcludeRuleFilter otherSpec : multipleExcludeRulesSpec.excludeSpecs) {
                    mergeExcludeRules(thisSpec, otherSpec, merged);
                }
            }
            if (merged.isEmpty()) {
                return ALL_SPEC;
            }
            return new MultipleExcludeRulesSpec(merged);
        }

        private boolean canMerge(DefaultModuleExcludeRuleFilter excludeSpec) {
            return excludeSpec instanceof ExcludeAllModulesSpec
                || excludeSpec instanceof ArtifactExcludeSpec
                || excludeSpec instanceof GroupNameExcludeSpec
                || excludeSpec instanceof ModuleNameExcludeSpec
                || excludeSpec instanceof ModuleIdExcludeSpec;
        }

        // Add exclusions to the list that will exclude modules/artifacts that are excluded by both of the candidate rules.
        private void mergeExcludeRules(DefaultModuleExcludeRuleFilter spec1, DefaultModuleExcludeRuleFilter spec2, List<DefaultModuleExcludeRuleFilter> merged) {
            if (spec1 instanceof ExcludeAllModulesSpec) {
                // spec1 excludes everything: only accept if spec2 accepts
                merged.add(spec2);
            } else if (spec2 instanceof ExcludeAllModulesSpec) {
                // spec2 excludes everything: only accept if spec1 accepts
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

        private void mergeExcludeRules(GroupNameExcludeSpec spec1, DefaultModuleExcludeRuleFilter spec2, List<DefaultModuleExcludeRuleFilter> merged) {
            if (spec2 instanceof GroupNameExcludeSpec) {
                // Intersection of 2 group excludes does nothing unless excluded groups match
                GroupNameExcludeSpec groupNameExcludeSpec = (GroupNameExcludeSpec) spec2;
                if (spec1.group.equals(groupNameExcludeSpec.group)) {
                    merged.add(spec1);
                }
            } else if (spec2 instanceof ModuleNameExcludeSpec) {
                // Intersection of group & module name exclude only excludes module with matching group + name
                ModuleNameExcludeSpec moduleNameExcludeSpec = (ModuleNameExcludeSpec) spec2;
                merged.add(new ModuleIdExcludeSpec(spec1.group, moduleNameExcludeSpec.module));
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

        private void mergeExcludeRules(ModuleNameExcludeSpec spec1, DefaultModuleExcludeRuleFilter spec2, List<DefaultModuleExcludeRuleFilter> merged) {
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
    }

    /**
     * A spec that selects those artifacts and modules that satisfy _any_ of the supplied exclude rules.
     */
    private static class UnionSpec extends CompositeSpec {
        private final List<DefaultModuleExcludeRuleFilter> specs;

        public UnionSpec(List<DefaultModuleExcludeRuleFilter> specs) {
            this.specs = specs;
        }

        @Override
        Collection<DefaultModuleExcludeRuleFilter> getSpecs() {
            return specs;
        }

        /**
         * Can unpack into constituents when creating a larger union.
         */
        @Override
        protected void unpackUnion(Collection<DefaultModuleExcludeRuleFilter> specs) {
            specs.addAll(this.specs);
        }

        @Override
        protected boolean acceptsAllModules() {
            for (DefaultModuleExcludeRuleFilter excludeSpec : specs) {
                if (excludeSpec.acceptsAllModules()) {
                    return true;
                }
            }
            return false;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            for (DefaultModuleExcludeRuleFilter spec : specs) {
                if (spec.acceptModule(element)) {
                    return true;
                }
            }

            return false;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            for (DefaultModuleExcludeRuleFilter spec : specs) {
                if (spec.acceptArtifact(module, artifact)) {
                    return true;
                }
            }

            return false;
        }

        public boolean acceptsAllArtifacts() {
            for (DefaultModuleExcludeRuleFilter spec : specs) {
                if (spec.acceptsAllArtifacts()) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * A ModuleResolutionFilter that accepts any module that has a module id other than the one specified.
     * Accepts all artifacts.
     */
    private static class ModuleIdExcludeSpec extends DefaultModuleExcludeRuleFilter {
        private final ModuleIdentifier moduleId;

        public ModuleIdExcludeSpec(String group, String name) {
            this.moduleId = DefaultModuleIdentifier.newId(group, name);
        }

        @Override
        public String toString() {
            return String.format("{module-id %s}", moduleId);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            ModuleIdExcludeSpec other = (ModuleIdExcludeSpec) o;
            return moduleId.equals(other.moduleId);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            ModuleIdExcludeSpec moduleIdExcludeSpec = (ModuleIdExcludeSpec) other;
            return moduleId.equals(moduleIdExcludeSpec.moduleId);
        }

        public boolean acceptModule(ModuleIdentifier module) {
            return !module.equals(moduleId);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }

        public boolean acceptsAllArtifacts() {
            return true;
        }
    }

    /**
     * A ModuleResolutionFilter that accepts any module that has a name other than the one specified.
     * Accepts all artifacts.
     */
    private static class ModuleNameExcludeSpec extends DefaultModuleExcludeRuleFilter {
        private final String module;

        private ModuleNameExcludeSpec(String module) {
            this.module = module;
        }

        @Override
        public String toString() {
            return String.format("{module %s}", module);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            ModuleNameExcludeSpec other = (ModuleNameExcludeSpec) o;
            return module.equals(other.module);
        }

        @Override
        public int hashCode() {
            return module.hashCode();
        }

        @Override
        public boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            ModuleNameExcludeSpec moduleNameExcludeSpec = (ModuleNameExcludeSpec) other;
            return module.equals(moduleNameExcludeSpec.module);
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return !element.getName().equals(module);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }

        public boolean acceptsAllArtifacts() {
            return true;
        }
    }

    /**
     * A ModuleResolutionFilter that accepts any module that has a group other than the one specified.
     * Accepts all artifacts.
     */
    private static class GroupNameExcludeSpec extends DefaultModuleExcludeRuleFilter {
        private final String group;

        private GroupNameExcludeSpec(String group) {
            this.group = group;
        }

        @Override
        public String toString() {
            return String.format("{group %s}", group);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            GroupNameExcludeSpec other = (GroupNameExcludeSpec) o;
            return group.equals(other.group);
        }

        @Override
        public int hashCode() {
            return group.hashCode();
        }

        @Override
        public boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            GroupNameExcludeSpec groupNameExcludeSpec = (GroupNameExcludeSpec) other;
            return group.equals(groupNameExcludeSpec.group);
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return !element.getGroup().equals(group);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }

        public boolean acceptsAllArtifacts() {
            return true;
        }
    }

    private static class ExcludeAllModulesSpec extends DefaultModuleExcludeRuleFilter {
        @Override
        public String toString() {
            return "{all modules}";
        }

        @Override
        public boolean equals(Object o) {
            return o == this || !(o == null || o.getClass() != getClass());
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            return true;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return false;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }

        public boolean acceptsAllArtifacts() {
            return true;
        }
    }

    /**
     * A ModuleResolutionFilter that accepts any module/artifact that doesn't match the exclude rule.
     */
    private static class IvyPatternMatcherExcludeRuleSpec extends DefaultModuleExcludeRuleFilter {
        private final ModuleIdentifier moduleId;
        private final IvyArtifactName ivyArtifactName;
        private final PatternMatcher matcher;
        private final boolean isArtifactExclude;

        private IvyPatternMatcherExcludeRuleSpec(ExcludeRule rule) {
            this.moduleId = DefaultModuleIdentifier.newId(rule.getId().getModuleId().getOrganisation(), rule.getId().getModuleId().getName());
            this.ivyArtifactName = new DefaultIvyArtifactName(rule.getId().getName(), rule.getId().getType(), rule.getId().getExt());
            this.matcher = rule.getMatcher();
            isArtifactExclude = !isWildcard(ivyArtifactName.getName()) || !isWildcard(ivyArtifactName.getType()) || !isWildcard(ivyArtifactName.getExtension());
        }

        @Override
        public String toString() {
            return String.format("{exclude-rule %s:%s with matcher %s}", moduleId, ivyArtifactName, matcher.getName());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            IvyPatternMatcherExcludeRuleSpec other = (IvyPatternMatcherExcludeRuleSpec) o;
            return doAcceptsSameModulesAs(other);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode() ^ ivyArtifactName.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            IvyPatternMatcherExcludeRuleSpec otherSpec = (IvyPatternMatcherExcludeRuleSpec) other;
            return moduleId.equals(otherSpec.moduleId)
                    && ivyArtifactName.equals(otherSpec.ivyArtifactName)
                    && matcher.getName().equals(otherSpec.matcher.getName());
        }

        @Override
        protected boolean acceptsAllModules() {
            return isArtifactExclude;
        }

        public boolean acceptModule(ModuleIdentifier module) {
            return isArtifactExclude || !(matches(moduleId.getGroup(), module.getGroup()) && matches(moduleId.getName(), module.getName()));
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            if (isArtifactExclude) {
                return !(matches(moduleId.getGroup(), module.getGroup())
                        && matches(moduleId.getName(), module.getName())
                        && matches(ivyArtifactName.getName(), artifact.getName())
                        && matches(ivyArtifactName.getExtension(), artifact.getExtension())
                        && matches(ivyArtifactName.getType(), artifact.getType()));
            }
            return true;
        }

        public boolean acceptsAllArtifacts() {
            return !isArtifactExclude;
        }

        private boolean matches(String expression, String input) {
            return matcher.getMatcher(expression).matches(input);
        }
    }

    /**
     * A ModuleResolutionFilter that accepts any artifact that doesn't match the exclude rule.
     * Accepts all modules.
     */
    private static class ArtifactExcludeSpec extends DefaultModuleExcludeRuleFilter {
        private final ModuleIdentifier moduleId;
        private final IvyArtifactName ivyArtifactName;

        private ArtifactExcludeSpec(ArtifactId artifactId) {
            this.moduleId = DefaultModuleIdentifier.newId(artifactId.getModuleId().getOrganisation(), artifactId.getModuleId().getName());
            this.ivyArtifactName = new DefaultIvyArtifactName(artifactId.getName(), artifactId.getType(), artifactId.getExt());
        }

        @Override
        public String toString() {
            return String.format("{artifact %s:%s}", moduleId, ivyArtifactName);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            ArtifactExcludeSpec other = (ArtifactExcludeSpec) o;
            return moduleId.equals(other.moduleId) && ivyArtifactName.equals(other.ivyArtifactName);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode() ^ ivyArtifactName.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleExcludeRuleFilter other) {
            return true;
        }

        @Override
        protected boolean acceptsAllModules() {
            return true;
        }

        public boolean acceptModule(ModuleIdentifier module) {
            return true;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return !(matches(moduleId.getGroup(), module.getGroup())
                    && matches(moduleId.getName(), module.getName())
                    && matches(ivyArtifactName.getName(), artifact.getName())
                    && matches(ivyArtifactName.getExtension(), artifact.getExtension())
                    && matches(ivyArtifactName.getType(), artifact.getType()));
        }

        public boolean acceptsAllArtifacts() {
            return false;
        }

        private boolean matches(String expression, String input) {
            return isWildcard(expression) || expression.equals(input);
        }
    }
}
