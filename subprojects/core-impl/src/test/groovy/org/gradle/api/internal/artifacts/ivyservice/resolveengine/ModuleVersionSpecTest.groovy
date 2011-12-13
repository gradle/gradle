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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import spock.lang.Specification

class ModuleVersionSpecTest extends Specification {
    def "accepts all module versions by default"() {
        def spec = ModuleVersionSpec.forExcludes()

        expect:
        spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
    }

    def "does not accept module version that matches any exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
        spec.isSatisfiedBy(ModuleId.newInstance("org", "other"))
    }

    def "accepts all module versions when merged with empty set of exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        def union = spec.union()
        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        union.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
    }

    def "does not accept module versions that are not accepted by any merged exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        def union = spec.union(rule1)

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        !union.isSatisfiedBy(ModuleId.newInstance("org", "module"))

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
        union.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
    }

    def excludeRule(String org, String module) {
        return new DefaultExcludeRule(new ArtifactId(ModuleId.newInstance(org, module), "ivy", "ivy", "ivy"), ExactPatternMatcher.INSTANCE, [:])
    }
}
