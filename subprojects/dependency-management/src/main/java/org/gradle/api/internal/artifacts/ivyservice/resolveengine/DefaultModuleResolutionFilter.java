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
public abstract class DefaultModuleResolutionFilter implements ModuleResolutionFilter {
    private static final AcceptAllSpec ALL_SPEC = new AcceptAllSpec();
    private static final String WILDCARD = "*";

    /**
     * Returns a spec that accepts everything.
     */
    public static ModuleResolutionFilter all() {
        return ALL_SPEC;
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleResolutionFilter excludeAny(ExcludeRule... excludeRules) {
        if (excludeRules.length == 0) {
            return ALL_SPEC;
        }
        return new ExcludeRuleBackedSpec(Arrays.asList(excludeRules));
    }

    /**
     * Returns a spec that accepts only those module versions that do not match any of the given exclude rules.
     */
    public static ModuleResolutionFilter excludeAny(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return ALL_SPEC;
        }
        return new ExcludeRuleBackedSpec(excludeRules);
    }

    private static boolean isWildcard(String attribute) {
        return WILDCARD.equals(attribute);
    }

    public ModuleResolutionFilter union(ModuleResolutionFilter other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return other;
        }
        if (this == ALL_SPEC) {
            return this;
        }
        List<DefaultModuleResolutionFilter> specs = new ArrayList<DefaultModuleResolutionFilter>();
        unpackUnion(specs);
        ((DefaultModuleResolutionFilter) other).unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            DefaultModuleResolutionFilter spec = specs.get(i);
            DefaultModuleResolutionFilter merged = null;
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

    protected void unpackUnion(Collection<DefaultModuleResolutionFilter> specs) {
        specs.add(this);
    }

    /**
     * Returns the union of this filter and the given filter. Returns null if not recognized.
     */
    protected DefaultModuleResolutionFilter doUnion(DefaultModuleResolutionFilter other) {
        return null;
    }

    public final boolean acceptsSameModulesAs(ModuleResolutionFilter filter) {
        if (filter == this) {
            return true;
        }
        DefaultModuleResolutionFilter other = (DefaultModuleResolutionFilter) filter;
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
    protected boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
        return false;
    }

    protected boolean acceptsAllModules() {
        return false;
    }

    /**
     * Returns a spec that accepts the intersection of those module versions that are accepted by this spec and the given spec.
     */
    public ModuleResolutionFilter intersect(ModuleResolutionFilter other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return this;
        }
        if (this == ALL_SPEC) {
            return other;
        }

        List<DefaultModuleResolutionFilter> specs = new ArrayList<DefaultModuleResolutionFilter>();
        unpackIntersection(specs);
        ((DefaultModuleResolutionFilter) other).unpackIntersection(specs);

        return new ExcludeRuleBackedSpec(specs);
    }

    protected void unpackIntersection(Collection<DefaultModuleResolutionFilter> specs) {
        specs.add(this);
    }

    private static class AcceptAllSpec extends DefaultModuleResolutionFilter {
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
    }

    private static abstract class CompositeSpec extends DefaultModuleResolutionFilter {
        abstract Collection<DefaultModuleResolutionFilter> getSpecs();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append(getClass().getSimpleName());
            for (DefaultModuleResolutionFilter spec : getSpecs()) {
                builder.append(' ');
                builder.append(spec);
            }
            builder.append("}");
            return builder.toString();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
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
            for (DefaultModuleResolutionFilter thisSpec : getSpecs()) {
                boolean found = false;
                for (DefaultModuleResolutionFilter otherSpec : spec.getSpecs()) {
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

    private static class ExcludeRuleBackedSpec extends CompositeSpec {
        private final Set<DefaultModuleResolutionFilter> excludeSpecs = new HashSet<DefaultModuleResolutionFilter>();

        private ExcludeRuleBackedSpec(Iterable<ExcludeRule> excludeRules) {
            for (ExcludeRule rule : excludeRules) {

                if (!(rule.getMatcher() instanceof ExactPatternMatcher)) {
                    excludeSpecs.add(new ExcludeRuleSpec(rule));
                    continue;
                }

                ArtifactId artifactId = rule.getId();
                ModuleId moduleId = artifactId.getModuleId();
                boolean anyOrganisation = isWildcard(moduleId.getOrganisation());
                boolean anyModule = isWildcard(moduleId.getName());
                boolean anyArtifact = isWildcard(artifactId.getName()) && isWildcard(artifactId.getType()) && isWildcard(artifactId.getExt());

                if (anyArtifact) {
                    if (!anyOrganisation && !anyModule) {
                        excludeSpecs.add(new ModuleIdSpec(moduleId.getOrganisation(), moduleId.getName()));
                    } else if (!anyModule) {
                        excludeSpecs.add(new ModuleNameSpec(moduleId.getName()));
                    } else if (!anyOrganisation) {
                        excludeSpecs.add(new GroupNameSpec(moduleId.getOrganisation()));
                    } else {
                        excludeSpecs.add(new ExcludeAllModulesSpec());
                    }
                } else {
                    excludeSpecs.add(new ArtifactSpec(rule));
                }
            }
        }

        public ExcludeRuleBackedSpec(Collection<DefaultModuleResolutionFilter> specs) {
            this.excludeSpecs.addAll(specs);
        }

        @Override
        Collection<DefaultModuleResolutionFilter> getSpecs() {
            return excludeSpecs;
        }

        @Override
        protected void unpackIntersection(Collection<DefaultModuleResolutionFilter> specs) {
            specs.addAll(excludeSpecs);
        }

        @Override
        protected boolean acceptsAllModules() {
            for (DefaultModuleResolutionFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptsAllModules()) {
                    return false;
                }
            }
            return true;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            for (DefaultModuleResolutionFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptModule(element)) {
                    return false;
                }
            }
            return true;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            for (DefaultModuleResolutionFilter excludeSpec : excludeSpecs) {
                if (!excludeSpec.acceptArtifact(module, artifact)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected DefaultModuleResolutionFilter doUnion(DefaultModuleResolutionFilter other) {
            if (!(other instanceof ExcludeRuleBackedSpec)) {
                return super.doUnion(other);
            }

            ExcludeRuleBackedSpec excludeRuleBackedSpec = (ExcludeRuleBackedSpec) other;
            if (excludeSpecs.equals(excludeRuleBackedSpec.excludeSpecs)) {
                return this;
            }

            // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
            for (DefaultModuleResolutionFilter excludeSpec : excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }
            for (DefaultModuleResolutionFilter excludeSpec : excludeRuleBackedSpec.excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }

            // Calculate the intersection of the rules
            List<DefaultModuleResolutionFilter> merged = new ArrayList<DefaultModuleResolutionFilter>();
            for (DefaultModuleResolutionFilter thisSpec : excludeSpecs) {
                for (DefaultModuleResolutionFilter otherSpec : excludeRuleBackedSpec.excludeSpecs) {
                    intersect(thisSpec, otherSpec, merged);
                }
            }
            if (merged.isEmpty()) {
                return ALL_SPEC;
            }
            return new ExcludeRuleBackedSpec(merged);
        }

        private void intersect(DefaultModuleResolutionFilter spec1, DefaultModuleResolutionFilter spec2, List<DefaultModuleResolutionFilter> merged) {
            if (spec1 instanceof ArtifactSpec) {
                merged.add(spec1);
            } else if (spec2 instanceof ArtifactSpec) {
                merged.add(spec2);
            } else if (spec1 instanceof GroupNameSpec) {
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

        private void intersect(GroupNameSpec spec1, DefaultModuleResolutionFilter spec2, List<DefaultModuleResolutionFilter> merged) {
            if (spec2 instanceof GroupNameSpec) {
                GroupNameSpec groupNameSpec = (GroupNameSpec) spec2;
                if (spec1.group.equals(groupNameSpec.group)) {
                    merged.add(spec1);
                }
            } else if (spec2 instanceof ModuleNameSpec) {
                ModuleNameSpec moduleNameSpec = (ModuleNameSpec) spec2;
                merged.add(new ModuleIdSpec(spec1.group, moduleNameSpec.module));
            } else if (spec2 instanceof ModuleIdSpec) {
                ModuleIdSpec moduleIdSpec = (ModuleIdSpec) spec2;
                if (moduleIdSpec.moduleId.getGroup().equals(spec1.group)) {
                    merged.add(spec2);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private void intersect(ModuleNameSpec spec1, DefaultModuleResolutionFilter spec2, List<DefaultModuleResolutionFilter> merged) {
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
    }

    private static class UnionSpec extends CompositeSpec {
        private final List<DefaultModuleResolutionFilter> specs;

        public UnionSpec(List<DefaultModuleResolutionFilter> specs) {
            this.specs = specs;
        }

        @Override
        Collection<DefaultModuleResolutionFilter> getSpecs() {
            return specs;
        }

        @Override
        protected void unpackUnion(Collection<DefaultModuleResolutionFilter> specs) {
            specs.addAll(this.specs);
        }

        @Override
        protected boolean acceptsAllModules() {
            for (DefaultModuleResolutionFilter excludeSpec : specs) {
                if (excludeSpec.acceptsAllModules()) {
                    return true;
                }
            }
            return false;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            for (DefaultModuleResolutionFilter spec : specs) {
                if (spec.acceptModule(element)) {
                    return true;
                }
            }

            return false;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            for (DefaultModuleResolutionFilter spec : specs) {
                if (spec.acceptArtifact(module, artifact)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static class ModuleIdSpec extends DefaultModuleResolutionFilter {
        private final ModuleIdentifier moduleId;

        public ModuleIdSpec(String group, String name) {
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
            ModuleIdSpec other = (ModuleIdSpec) o;
            return moduleId.equals(other.moduleId);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
            ModuleIdSpec moduleIdSpec = (ModuleIdSpec) other;
            return moduleId.equals(moduleIdSpec.moduleId);
        }

        public boolean acceptModule(ModuleIdentifier module) {
            return !module.equals(moduleId);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }
    }

    private static class ModuleNameSpec extends DefaultModuleResolutionFilter {
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
        public boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
            ModuleNameSpec moduleNameSpec = (ModuleNameSpec) other;
            return module.equals(moduleNameSpec.module);
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return !element.getName().equals(module);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }
    }

    private static class GroupNameSpec extends DefaultModuleResolutionFilter {
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
        public boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
            GroupNameSpec groupNameSpec = (GroupNameSpec) other;
            return group.equals(groupNameSpec.group);
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return !element.getGroup().equals(group);
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }
    }

    private static class ExcludeAllModulesSpec extends DefaultModuleResolutionFilter {
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
        public boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
            return true;
        }

        public boolean acceptModule(ModuleIdentifier element) {
            return false;
        }

        public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
            return true;
        }
    }

    private static class ExcludeRuleSpec extends DefaultModuleResolutionFilter {
        private final ModuleIdentifier moduleId;
        private final IvyArtifactName ivyArtifactName;
        private final PatternMatcher matcher;
        private final boolean isArtifactExclude;

        private ExcludeRuleSpec(ExcludeRule rule) {
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
            ExcludeRuleSpec other = (ExcludeRuleSpec) o;
            return doAcceptsSameModulesAs(other);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode() ^ ivyArtifactName.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
            ExcludeRuleSpec otherSpec = (ExcludeRuleSpec) other;
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

        private boolean matches(String expression, String input) {
            return matcher.getMatcher(expression).matches(input);
        }
    }

    private static class ArtifactSpec extends DefaultModuleResolutionFilter {
        private final ModuleIdentifier moduleId;
        private final IvyArtifactName ivyArtifactName;
        private final PatternMatcher matcher;

        private ArtifactSpec(ExcludeRule rule) {
            this.moduleId = DefaultModuleIdentifier.newId(rule.getId().getModuleId().getOrganisation(), rule.getId().getModuleId().getName());
            this.ivyArtifactName = new DefaultIvyArtifactName(rule.getId().getName(), rule.getId().getType(), rule.getId().getExt());
            this.matcher = rule.getMatcher();
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
            ArtifactSpec other = (ArtifactSpec) o;
            return moduleId.equals(other.moduleId) && ivyArtifactName.equals(other.ivyArtifactName);
        }

        @Override
        public int hashCode() {
            return moduleId.hashCode() ^ ivyArtifactName.hashCode();
        }

        @Override
        protected boolean doAcceptsSameModulesAs(DefaultModuleResolutionFilter other) {
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

        private boolean matches(String expression, String input) {
            return matcher.getMatcher(expression).matches(input);
        }
    }
}
