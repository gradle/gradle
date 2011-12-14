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

    def "union accepts all module versions when one spec has empty set of exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes()

        expect:
        spec.union(spec2) == spec2
        spec2.union(spec) == spec2
    }

    def "union of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        spec.union(spec) == spec
    }

    def "union accept module version that is accepted by any merged exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule1)

        expect:
        def union = spec.union(spec2)

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        !union.isSatisfiedBy(ModuleId.newInstance("org", "module"))

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
        union.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
    }

    def "intersection accepts those module versions accepted by other spec when one spec has empty set of exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes()

        expect:
        spec.intersect(spec2) == spec
        spec2.intersect(spec) == spec
    }

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        spec.intersect(spec) == spec
    }

    def "intersection does not accept module version that is not accepted by any merged exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule1)

        expect:
        def intersect = spec.intersect(spec2)

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        !intersect.isSatisfiedBy(ModuleId.newInstance("org", "module"))

        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
        !intersect.isSatisfiedBy(ModuleId.newInstance("org", "module2"))

        spec.isSatisfiedBy(ModuleId.newInstance("org", "module3"))
        spec2.isSatisfiedBy(ModuleId.newInstance("org", "module3"))
        intersect.isSatisfiedBy(ModuleId.newInstance("org", "module3"))
    }

    def excludeRule(String org, String module) {
        return new DefaultExcludeRule(new ArtifactId(ModuleId.newInstance(org, module), "ivy", "ivy", "ivy"), ExactPatternMatcher.INSTANCE, [:])
    }
}
