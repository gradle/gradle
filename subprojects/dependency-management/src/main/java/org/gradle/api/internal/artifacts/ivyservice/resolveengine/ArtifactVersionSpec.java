/*
 * Copyright 2014 the original author or authors.
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
import org.apache.ivy.plugins.matcher.MatcherHelper;
import org.gradle.api.specs.Spec;

import java.util.*;

public abstract class ArtifactVersionSpec implements Spec<ArtifactId>, Mergeable<ArtifactVersionSpec> {
    private static final AcceptAllSpec ALL_SPEC = new AcceptAllSpec();

    public static ArtifactVersionSpec forExcludes(ExcludeRule... excludeRules) {
        return forExcludes(Arrays.asList(excludeRules));
    }

    public static ArtifactVersionSpec forExcludes(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return ALL_SPEC;
        }
        return new ExcludeRuleBackedSpec(excludeRules);
    }

    public ArtifactVersionSpec union(ArtifactVersionSpec other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return other;
        }
        if (this == ALL_SPEC) {
            return this;
        }
        List<ArtifactVersionSpec> specs = new ArrayList<ArtifactVersionSpec>();
        unpackUnion(specs);
        other.unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            ArtifactVersionSpec spec = specs.get(i);
            ArtifactVersionSpec merged = null;
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

    protected void unpackUnion(Collection<ArtifactVersionSpec> specs) {
        specs.add(this);
    }

    protected ArtifactVersionSpec doUnion(ArtifactVersionSpec other) {
        return null;
    }

    public ArtifactVersionSpec intersect(ArtifactVersionSpec other) {
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

    protected ArtifactVersionSpec doIntersection(ArtifactVersionSpec other) {
        return new IntersectSpec(this, other);
    }

    private static class AcceptAllSpec extends ArtifactVersionSpec {
        @Override
        public String toString() {
            return "{accept-all}";
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            return true;
        }
    }

    private static abstract class CompositeSpec extends ArtifactVersionSpec {
        abstract Collection<ArtifactVersionSpec> getSpecs();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append(getClass().getSimpleName());
            for (ArtifactVersionSpec spec : getSpecs()) {
                builder.append(' ');
                builder.append(spec);
            }
            builder.append("}");
            return builder.toString();
        }
    }

    private static class IntersectSpec extends CompositeSpec {
        private final List<ArtifactVersionSpec> specs;

        private IntersectSpec(ArtifactVersionSpec... specs) {
            this.specs = Arrays.asList(specs);
        }

        @Override
        Collection<ArtifactVersionSpec> getSpecs() {
            return specs;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactVersionSpec spec : specs) {
                if (!spec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }
    }

    static class ExcludeRuleBackedSpec extends CompositeSpec {
        private final Set<ArtifactVersionSpec> excludeSpecs = new HashSet<ArtifactVersionSpec>();

        private ExcludeRuleBackedSpec(Iterable<ExcludeRule> excludeRules) {
            for (ExcludeRule rule : excludeRules) {
                excludeSpecs.add(new ExcludeRuleSpec(rule));
            }
        }

        public ExcludeRuleBackedSpec(Collection<ArtifactVersionSpec> specs) {
            this.excludeSpecs.addAll(specs);
        }

        @Override
        Collection<ArtifactVersionSpec> getSpecs() {
            return excludeSpecs;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactVersionSpec excludeSpec : excludeSpecs) {
                if (excludeSpec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected ArtifactVersionSpec doUnion(ArtifactVersionSpec other) {
            if (!(other instanceof ExcludeRuleBackedSpec)) {
                return super.doUnion(other);
            }

            ExcludeRuleBackedSpec excludeRuleBackedSpec = (ExcludeRuleBackedSpec) other;
            if (excludeSpecs.equals(excludeRuleBackedSpec.excludeSpecs)) {
                return this;
            }

            // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
            for (ArtifactVersionSpec excludeSpec : excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }
            for (ArtifactVersionSpec excludeSpec : excludeRuleBackedSpec.excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }

            List<ArtifactVersionSpec> merged = new ArrayList<ArtifactVersionSpec>();

            if (merged.isEmpty()) {
                return ALL_SPEC;
            }
            return new ExcludeRuleBackedSpec(merged);
        }
    }

    static class UnionSpec extends CompositeSpec {
        private final List<ArtifactVersionSpec> specs;

        public UnionSpec(List<ArtifactVersionSpec> specs) {
            this.specs = specs;
        }

        @Override
        Collection<ArtifactVersionSpec> getSpecs() {
            return specs;
        }

        @Override
        protected void unpackUnion(Collection<ArtifactVersionSpec> specs) {
            specs.addAll(this.specs);
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactVersionSpec spec : specs) {
                if (spec.isSatisfiedBy(element)) {
                    return true;
                }
            }

            return false;
        }
    }

    static class ExcludeRuleSpec extends ArtifactVersionSpec {
        private final ExcludeRule rule;

        public ExcludeRuleSpec(ExcludeRule rule) {
            this.rule = rule;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            return MatcherHelper.matches(rule.getMatcher(), rule.getId(), element);
        }
    }
}
