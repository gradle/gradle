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
import org.apache.ivy.plugins.matcher.MatcherHelper;
import org.gradle.api.specs.Spec;

public abstract class ModuleVersionSpec implements Spec<ModuleId> {

    public static ModuleVersionSpec forExcludes(ExcludeRule... excludeRules) {
        return new ExcludeRuleBackedSpec(excludeRules);
    }

    /**
     * Returns a spec which is accepts the union of those module versions that are accepted by this spec, and a spec with the given exclude rules.
     *
     * @return The new spec. Returns this if the union == the set of module versions that are accepted by this spec.
     */
    public ModuleVersionSpec union(ExcludeRule... excludeRules) {
        if (excludeRules.length == 0) {
            return forExcludes();
        }
        return new UnionSpec(this, forExcludes(excludeRules));
    }

    private static class ExcludeRuleBackedSpec extends ModuleVersionSpec {
        private final ExcludeRule[] excludeRules;

        private ExcludeRuleBackedSpec(ExcludeRule[] excludeRules) {
            this.excludeRules = excludeRules;
        }

        public boolean isSatisfiedBy(ModuleId element) {
            ArtifactId dummyArtifact = new ArtifactId(element, "ivy", "ivy", "ivy");
            for (ExcludeRule excludeRule : excludeRules) {
                if (MatcherHelper.matches(excludeRule.getMatcher(), excludeRule.getId(), dummyArtifact)) {
                    return false;
                }
            }

            return true;
        }
    }
    
    private static class UnionSpec extends ModuleVersionSpec {
        private final ModuleVersionSpec[] specs;

        public UnionSpec(ModuleVersionSpec... specs) {
            this.specs = specs;
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
}
