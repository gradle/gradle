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

public abstract class ArtifactIdSpec implements Spec<ArtifactId>, Mergeable<ArtifactIdSpec> {
    private static final AcceptAllSpec ALL_SPEC = new AcceptAllSpec();

    public static ArtifactIdSpec forExcludes(ExcludeRule... excludeRules) {
        return forExcludes(Arrays.asList(excludeRules));
    }

    public static ArtifactIdSpec forExcludes(Collection<ExcludeRule> excludeRules) {
        if (excludeRules.isEmpty()) {
            return ALL_SPEC;
        }
        return new ExcludeRuleBackedSpec(excludeRules);
    }

    public ArtifactIdSpec union(ArtifactIdSpec other) {
        if (other == this) {
            return this;
        }
        if (other == ALL_SPEC) {
            return other;
        }
        if (this == ALL_SPEC) {
            return this;
        }
        List<ArtifactIdSpec> specs = new ArrayList<ArtifactIdSpec>();
        unpackUnion(specs);
        other.unpackUnion(specs);
        for (int i = 0; i < specs.size();) {
            ArtifactIdSpec spec = specs.get(i);
            ArtifactIdSpec merged = null;
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

    protected void unpackUnion(Collection<ArtifactIdSpec> specs) {
        specs.add(this);
    }

    protected ArtifactIdSpec doUnion(ArtifactIdSpec other) {
        return null;
    }

    public ArtifactIdSpec intersect(ArtifactIdSpec other) {
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

    protected ArtifactIdSpec doIntersection(ArtifactIdSpec other) {
        return new IntersectSpec(this, other);
    }

    private static class AcceptAllSpec extends ArtifactIdSpec {
        @Override
        public String toString() {
            return "{accept-all}";
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            return true;
        }
    }

    private static abstract class CompositeSpec extends ArtifactIdSpec {
        abstract Collection<ArtifactIdSpec> getSpecs();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append(getClass().getSimpleName());
            for (ArtifactIdSpec spec : getSpecs()) {
                builder.append(' ');
                builder.append(spec);
            }
            builder.append("}");
            return builder.toString();
        }
    }

    private static class IntersectSpec extends CompositeSpec {
        private final List<ArtifactIdSpec> specs;

        private IntersectSpec(ArtifactIdSpec... specs) {
            this.specs = Arrays.asList(specs);
        }

        @Override
        Collection<ArtifactIdSpec> getSpecs() {
            return specs;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactIdSpec spec : specs) {
                if (!spec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }
    }

    static class ExcludeRuleBackedSpec extends CompositeSpec {
        private final Set<ArtifactIdSpec> excludeSpecs = new HashSet<ArtifactIdSpec>();

        private ExcludeRuleBackedSpec(Iterable<ExcludeRule> excludeRules) {
            for (ExcludeRule rule : excludeRules) {
                excludeSpecs.add(new ExcludeRuleSpec(rule));
            }
        }

        public ExcludeRuleBackedSpec(Collection<ArtifactIdSpec> specs) {
            this.excludeSpecs.addAll(specs);
        }

        @Override
        Collection<ArtifactIdSpec> getSpecs() {
            return excludeSpecs;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactIdSpec excludeSpec : excludeSpecs) {
                if (excludeSpec.isSatisfiedBy(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected ArtifactIdSpec doUnion(ArtifactIdSpec other) {
            if (!(other instanceof ExcludeRuleBackedSpec)) {
                return super.doUnion(other);
            }

            ExcludeRuleBackedSpec excludeRuleBackedSpec = (ExcludeRuleBackedSpec) other;
            if (excludeSpecs.equals(excludeRuleBackedSpec.excludeSpecs)) {
                return this;
            }

            // Can only merge exact match rules, so don't try if this or the other spec contains any other type of rule
            for (ArtifactIdSpec excludeSpec : excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }
            for (ArtifactIdSpec excludeSpec : excludeRuleBackedSpec.excludeSpecs) {
                if (excludeSpec instanceof ExcludeRuleSpec) {
                    return super.doUnion(other);
                }
            }

            List<ArtifactIdSpec> merged = new ArrayList<ArtifactIdSpec>();

            if (merged.isEmpty()) {
                return ALL_SPEC;
            }
            return new ExcludeRuleBackedSpec(merged);
        }
    }

    static class UnionSpec extends CompositeSpec {
        private final List<ArtifactIdSpec> specs;

        public UnionSpec(List<ArtifactIdSpec> specs) {
            this.specs = specs;
        }

        @Override
        Collection<ArtifactIdSpec> getSpecs() {
            return specs;
        }

        @Override
        protected void unpackUnion(Collection<ArtifactIdSpec> specs) {
            specs.addAll(this.specs);
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            for (ArtifactIdSpec spec : specs) {
                if (spec.isSatisfiedBy(element)) {
                    return true;
                }
            }

            return false;
        }
    }

    static class ExcludeRuleSpec extends ArtifactIdSpec {
        private final ExcludeRule rule;

        public ExcludeRuleSpec(ExcludeRule rule) {
            this.rule = rule;
        }

        public boolean isSatisfiedBy(ArtifactId element) {
            return MatcherHelper.matches(rule.getMatcher(), rule.getId(), element);
        }
    }
}
