/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.MatcherHelper;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.specs.Spec;

import java.util.*;

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleId;

/**
 * Manages sets of exclude rules, allowing union and intersection operations on the rules.
 *
 * <p>This class attempts to reduce execution time, by flattening union and intersection specs, at the cost of more analysis at construction time. This is taken advantage of by {@link
 * DependencyGraphBuilder}, on the assumption that there are many more edges in the dependency graph than there are exclude rules (ie we evaluate the rules much more often that we construct them).
 * </p>
 *
 * <p>Also, this class attempts to be quite accurate in determining if 2 specs will match exactly the same set of modules. {@link DependencyGraphBuilder} uses this to avoid traversing the
 * dependency graph of a particular version that has already been traversed when a new incoming edge is added (eg a newly discovered dependency) and when an incoming edge is removed (eg a conflict
 * evicts a version that depends on the given version). </p>
 */
public abstract class ModuleVersionSpec implements Spec<ModuleId> {
    private static final AcceptAllSpec ALL_SPEC = new AcceptAllSpec();

    public static ModuleVersionSpec forExcludes(ExcludeRule... excludeRules) {
        return forExcludes(Arrays.asList(excludeRules));
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the
     */
    public static ModuleVersionSpec forExcludes(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return ALL_SPEC;
        }
        return new ExcludeRuleBackedSpec(excludeRules);
    }

    /**
     * Returns a spec that accepts the union of those module versions that are accepted by this spec and the given spec.
     */
    public final ModuleVersionSpec union(ModuleVersionSpec other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return other;
        }
        if (this == ALL_SPEC) {
            return this;
        }
        List<ModuleVersionSpec> specs = new ArrayList<ModuleVersionSpec>();
        unpackUnion(specs);
        other.unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            ModuleVersionSpec spec = specs.get(i);
            ModuleVersionSpec merged = null;
            for (int j = i + 1; j < specs.size(); j++) {
                merged = spec.doUnion(specs.get(j));
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

    protected void unpackUnion(Collection<ModuleVersionSpec> specs) {
        specs.add(this);
    }

    protected ModuleVersionSpec doUnion(ModuleVersionSpec other) {
        return null;
    }

    /**
     * Determines if this spec accepts the same set of modules as the given spec.
     *
     * @return true if the specs accept the same set of modules. Returns false if they may not, or if it is unknown.
     */
    public final boolean acceptsSameModulesAs(ModuleVersionSpec other) {
        if (other == this) {
            return true;
        }
        if (!other.getClass().equals(getClass())) {
            return false;
        }
        return doAcceptsSameModulesAs(other);
    }

    /**
     * Only called when this and the other spec have the same class.
     */
    protected boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
        return false;
    }

    /**
     * Returns a spec that accepts the intersection of those module versions that are accepted by this spec and the given spec.
     */
    public final ModuleVersionSpec intersect(ModuleVersionSpec other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return this;
        }
        if (this == ALL_SPEC) {
            return other;
        }
        return doIntersection(other);
    }

    protected ModuleVersionSpec doIntersection(ModuleVersionSpec other) {
        return new IntersectSpec(this, other);
    }

    private static class AcceptAllSpec extends ModuleVersionSpec {
        @Override
        public String toString() {
            return "{accept-all}";
        }

        public boolean isSatisfiedBy(ModuleId element) {
            return true;
        }
    }

    private static abstract class CompositeSpec extends ModuleVersionSpec {
        abstract Collection<ModuleVersionSpec> getSpecs();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append(getClass().getSimpleName());
            for (ModuleVersionSpec spec : getSpecs()) {
                builder.append(' ');
                builder.append(spec);
            }
            builder.append("}");
            return builder.toString();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
            CompositeSpec spec = (CompositeSpec) other;
            return implies(spec) && spec.implies(this);
        }

        /**
         * Returns true if for every spec in this spec, there is a corresponding spec in the given spec that acceptsSameModulesAs().
         */
        protected boolean implies(CompositeSpec spec) {
            for (ModuleVersionSpec thisSpec : getSpecs()) {
                boolean found = false;
                for (ModuleVersionSpec otherSpec : spec.getSpecs()) {
                    if (thisSpec.acceptsSameModulesAs(otherSpec)) {
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

    static class ExcludeRuleBackedSpec extends CompositeSpec {
        private final Set<ModuleVersionSpec> excludeSpecs = new HashSet<ModuleVersionSpec>();

        private ExcludeRuleBackedSpec(Iterable<ExcludeRule> excludeRules) {
            for (ExcludeRule rule : excludeRules) {
                if (!(rule.getMatcher() instanceof ExactPatternMatcher)) {
                    excludeSpecs.add(new ExcludeRuleSpec(rule));
                    continue;
                }
                ModuleId moduleId = rule.getId().getModuleId();
                boolean wildcardGroup = PatternMatcher.ANY_EXPRESSION.equals(moduleId.getOrganisation());
                boolean wildcardModule = PatternMatcher.ANY_EXPRESSION.equals(moduleId.getName());
                if (wildcardGroup && wildcardModule) {
                    excludeSpecs.add(new ExcludeRuleSpec(rule));
                } else if (wildcardGroup) {
                    excludeSpecs.add(new ModuleNameSpec(moduleId.getName()));
                } else if (wildcardModule) {
                    excludeSpecs.add(new GroupNameSpec(moduleId.getOrganisation()));
                } else {
                    excludeSpecs.add(new ModuleIdSpec(moduleId));
                }
            }
        }

        public ExcludeRuleBackedSpec(Collection<ModuleVersionSpec> specs) {
            this.excludeSpecs.addAll(specs);
        }

        @Override
        Collection<ModuleVersionSpec> getSpecs() {
            return excludeSpecs;
        }

        public boolean isSatisfiedBy(ModuleId element) {
            for (ModuleVersionSpec excludeSpec : excludeSpecs) {
                if (excludeSpec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected ModuleVersionSpec doUnion(ModuleVersionSpec other) {
            if (!(other instanceof ExcludeRuleBackedSpec)) {
                return super.doUnion(other);
            }

            ExcludeRuleBackedSpec excludeRuleBackedSpec = (ExcludeRuleBackedSpec) other;
            if (excludeSpecs.equals(excludeRuleBackedSpec.excludeSpecs)) {
                return this;
            }

            // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
            for (ModuleVersionSpec excludeSpec : excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }
            for (ModuleVersionSpec excludeSpec : excludeRuleBackedSpec.excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }

            // Calculate the intersection of the rules
            List<ModuleVersionSpec> merged = new ArrayList<ModuleVersionSpec>();
            for (ModuleVersionSpec thisSpec : excludeSpecs) {
                for (ModuleVersionSpec otherSpec : excludeRuleBackedSpec.excludeSpecs) {
                    intersect(thisSpec, otherSpec, merged);
                }
            }
            if (merged.isEmpty()) {
                return ALL_SPEC;
            }
            return new ExcludeRuleBackedSpec(merged);
        }

        private void intersect(ModuleVersionSpec spec1, ModuleVersionSpec spec2, List<ModuleVersionSpec> merged) {
            if (spec1 instanceof GroupNameSpec) {
                intersect((GroupNameSpec) spec1, spec2, merged);
            } else if (spec2 instanceof GroupNameSpec) {
                intersect((GroupNameSpec) spec2, spec1, merged);
            } else if (spec1 instanceof ModuleNameSpec) {
                intersect((ModuleNameSpec) spec1, spec2, merged);
            } else if (spec2 instanceof ModuleNameSpec) {
                intersect((ModuleNameSpec) spec2, spec1, merged);
            } else if ((spec1 instanceof ModuleIdSpec) && (spec2 instanceof ModuleIdSpec)) {
                ModuleIdSpec moduleSpec1 = (ModuleIdSpec) spec1;
                ModuleIdSpec moduleSpec2 = (ModuleIdSpec) spec2;
                if (moduleSpec1.moduleId.equals(moduleSpec2.moduleId)) {
                    merged.add(moduleSpec1);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void intersect(GroupNameSpec spec1, ModuleVersionSpec spec2, List<ModuleVersionSpec> merged) {
            if (spec2 instanceof GroupNameSpec) {
                GroupNameSpec groupNameSpec = (GroupNameSpec) spec2;
                if (spec1.group.equals(groupNameSpec.group)) {
                    merged.add(spec1);
                }
            } else if (spec2 instanceof ModuleNameSpec) {
                ModuleNameSpec moduleNameSpec = (ModuleNameSpec) spec2;
                merged.add(new ModuleIdSpec(createModuleId(spec1.group, moduleNameSpec.module)));
            } else if (spec2 instanceof ModuleIdSpec) {
                ModuleIdSpec moduleIdSpec = (ModuleIdSpec) spec2;
                if (moduleIdSpec.moduleId.getOrganisation().equals(spec1.group)) {
                    merged.add(spec2);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void intersect(ModuleNameSpec spec1, ModuleVersionSpec spec2, List<ModuleVersionSpec> merged) {
            if (spec2 instanceof ModuleNameSpec) {
                ModuleNameSpec moduleNameSpec = (ModuleNameSpec) spec2;
                if (spec1.module.equals(moduleNameSpec.module)) {
                    merged.add(spec1);
                }
            } else if (spec2 instanceof ModuleIdSpec) {
                ModuleIdSpec moduleIdSpec = (ModuleIdSpec) spec2;
                if (moduleIdSpec.moduleId.getName().equals(spec1.module)) {
                    merged.add(spec2);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        protected ModuleVersionSpec doIntersection(ModuleVersionSpec other) {
            if (!(other instanceof ExcludeRuleBackedSpec)) {
                return super.doIntersection(other);
            }

            ExcludeRuleBackedSpec otherExcludeRuleSpec = (ExcludeRuleBackedSpec) other;
            Set<ModuleVersionSpec> allSpecs = new HashSet<ModuleVersionSpec>();
            allSpecs.addAll(excludeSpecs);
            allSpecs.addAll(otherExcludeRuleSpec.excludeSpecs);
            return new ExcludeRuleBackedSpec(allSpecs);
        }
    }

    static class UnionSpec extends CompositeSpec {
        private final List<ModuleVersionSpec> specs;

        public UnionSpec(List<ModuleVersionSpec> specs) {
            this.specs = specs;
        }

        @Override
        Collection<ModuleVersionSpec> getSpecs() {
            return specs;
        }

        @Override
        protected void unpackUnion(Collection<ModuleVersionSpec> specs) {
            specs.addAll(this.specs);
        }

        public boolean isSatisfiedBy(ModuleId element) {
            for (ModuleVersionSpec spec : specs) {
                if (spec.isSatisfiedBy(element)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static class IntersectSpec extends CompositeSpec {
        private final List<ModuleVersionSpec> specs;

        private IntersectSpec(ModuleVersionSpec... specs) {
            this.specs = Arrays.asList(specs);
        }

        @Override
        Collection<ModuleVersionSpec> getSpecs() {
            return specs;
        }

        public boolean isSatisfiedBy(ModuleId element) {
            for (ModuleVersionSpec spec : specs) {
                if (!spec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class ModuleIdSpec extends ModuleVersionSpec {
        private final ModuleId moduleId;

        private ModuleIdSpec(ModuleId moduleId) {
            this.moduleId = moduleId;
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
            ModuleIdSpec other = (ModuleIdSpec) o;
            return moduleId.equals(other.moduleId);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
            ModuleIdSpec moduleIdSpec = (ModuleIdSpec) other;
            return moduleId.equals(moduleIdSpec.moduleId);
        }

        public boolean isSatisfiedBy(ModuleId element) {
            return element.equals(moduleId);
        }
    }

    private static class ModuleNameSpec extends ModuleVersionSpec {
        private final String module;

        private ModuleNameSpec(String module) {
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
            ModuleNameSpec other = (ModuleNameSpec) o;
            return module.equals(other.module);
        }

        @Override
        public int hashCode() {
            return module.hashCode();
        }

        @Override
        public boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
            ModuleNameSpec moduleNameSpec = (ModuleNameSpec) other;
            return module.equals(moduleNameSpec.module);
        }

        public boolean isSatisfiedBy(ModuleId element) {
            return element.getName().equals(module);
        }
    }

    private static class GroupNameSpec extends ModuleVersionSpec {
        private final String group;

        private GroupNameSpec(String group) {
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
            GroupNameSpec other = (GroupNameSpec) o;
            return group.equals(other.group);
        }

        @Override
        public int hashCode() {
            return group.hashCode();
        }

        @Override
        public boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
            GroupNameSpec groupNameSpec = (GroupNameSpec) other;
            return group.equals(groupNameSpec.group);
        }

        public boolean isSatisfiedBy(ModuleId element) {
            return element.getOrganisation().equals(group);
        }
    }

    private static class ExcludeRuleSpec extends ModuleVersionSpec {
        private final ExcludeRule rule;

        private ExcludeRuleSpec(ExcludeRule rule) {
            this.rule = rule;
        }

        @Override
        public String toString() {
            return String.format("{exclude-rule %s}", rule);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            ExcludeRuleSpec other = (ExcludeRuleSpec) o;
            // Can't use equals(), as DefaultExcludeRule.equals() does not consider the pattern matcher
            return rule == other.rule;
        }

        @Override
        public int hashCode() {
            return rule.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(ModuleVersionSpec other) {
            ExcludeRuleSpec excludeRuleSpec = (ExcludeRuleSpec) other;
            // Can't use equals(), as DefaultExcludeRule.equals() does not consider the pattern matcher
            return rule == excludeRuleSpec.rule;
        }

        public boolean isSatisfiedBy(ModuleId element) {
            return MatcherHelper.matches(rule.getMatcher(), rule.getId().getModuleId(), element);
        }
    }
}
