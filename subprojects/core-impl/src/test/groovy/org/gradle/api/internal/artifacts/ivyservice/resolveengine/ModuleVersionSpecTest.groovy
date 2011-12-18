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
import org.apache.ivy.plugins.matcher.RegexpPatternMatcher

class ModuleVersionSpecTest extends Specification {
    def "accepts all module by default"() {
        def spec = ModuleVersionSpec.forExcludes()

        expect:
        spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
    }

    def "default specs accept the same modules as each other"() {
        expect:
        ModuleVersionSpec.forExcludes().acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
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

    def "specs with the same set of exclude rules accept the same modules as each other"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1)

        expect:
        spec.acceptsSameModulesAs(spec)
        spec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1))

        !spec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule2))
        !spec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
        !spec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2))
    }

    def "union accepts all modules when one spec has empty set of exclude rules"() {
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

    def "union of two specs with the same exclude rule instances returns one of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regExpExcludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule2, rule1)

        expect:
        spec.union(spec2) == spec
    }

    def "union of two specs with exact matching exclude rules is the intersection of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org", "module3")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule1, rule3)

        expect:
        def union = spec.union(spec2)
        union instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union.excludeRules.size() == 1
        union.excludeRules.contains(rule1)
    }

    def "union of two specs with disjoint exact matching exclude rules matches all modules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1)
        def spec2 = ModuleVersionSpec.forExcludes(rule2)

        expect:
        def union = spec.union(spec2)
        union == ModuleVersionSpec.forExcludes()
    }

    def "union of two specs with non-exact matching exclude rules is a union spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regExpExcludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1)
        def spec2 = ModuleVersionSpec.forExcludes(rule2)

        expect:
        def union = spec.union(spec2)
        union instanceof ModuleVersionSpec.UnionSpec
        union.specs.size() == 2
        union.specs[0] == spec
        union.specs[1] == spec2
    }

    def "union of union specs is the union of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = regExpExcludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1)
        def spec2 = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec3 = ModuleVersionSpec.forExcludes(rule3)

        expect:
        def union1 = spec.union(spec3)
        def union = union1.union(spec2)

        union instanceof ModuleVersionSpec.UnionSpec
        union.specs.size() == 2
        union.specs.any {
            it instanceof ModuleVersionSpec.ExcludeRuleBackedSpec && it.excludeRules == [rule1] as Set
        }
        union.specs.contains(spec3)
    }

    def "union accept module that is accepted by any merged exclude rule"() {
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

    def "unions accepts same modules when original specs accept same modules"() {
        def rule1 = regExpExcludeRule("org", "module")
        def rule2 = regExpExcludeRule("org", "module2")
        def rule3 = regExpExcludeRule("org", "module3")
        def spec1 = ModuleVersionSpec.forExcludes(rule1)
        def spec2 = ModuleVersionSpec.forExcludes(rule2)
        def spec3 = ModuleVersionSpec.forExcludes(rule3)

        expect:
        spec1.union(spec2).acceptsSameModulesAs(spec2.union(spec1))

        !spec1.union(spec2).acceptsSameModulesAs(spec2)
        !spec1.union(spec2).acceptsSameModulesAs(spec1)
        !spec1.union(spec2).acceptsSameModulesAs(spec1.union(spec3))
    }

    def "intersection accepts those modules accepted by other spec when one spec has empty set of exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes()

        expect:
        spec.intersect(spec2) == spec
        spec2.intersect(spec) == spec
    }

    def "intersection does not accept module that is not accepted by any merged exclude rules"() {
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

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)

        expect:
        spec.intersect(spec) == spec
    }

    def "intersection of two specs with exclude rules is the union of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule1)

        expect:
        def intersection = spec.intersect(spec2)
        intersection instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        intersection.excludeRules.size() == 2
        intersection.excludeRules.contains(rule1)
        intersection.excludeRules.contains(rule2)
    }

    def "intersections accepts same modules when original specs accept same modules"() {
        def rule1 = regExpExcludeRule("org", "module")
        def rule2 = regExpExcludeRule("org", "module2")
        def rule3 = regExpExcludeRule("org", "module3")
        def spec1 = ModuleVersionSpec.forExcludes(rule1).union(ModuleVersionSpec.forExcludes(rule2))
        def spec2 = ModuleVersionSpec.forExcludes(rule2).union(ModuleVersionSpec.forExcludes(rule1))
        def spec3 = ModuleVersionSpec.forExcludes(rule3)
        assert spec1.acceptsSameModulesAs(spec2)

        expect:
        spec1.intersect(spec2).acceptsSameModulesAs(spec2.intersect(spec1))

        !spec1.intersect(spec2).acceptsSameModulesAs(spec1)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec2)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec1.intersect(spec3))
    }

    def excludeRule(String org, String module) {
        return new DefaultExcludeRule(new ArtifactId(ModuleId.newInstance(org, module), "ivy", "ivy", "ivy"), ExactPatternMatcher.INSTANCE, [:])
    }

    def regExpExcludeRule(String org, String module) {
        return new DefaultExcludeRule(new ArtifactId(ModuleId.newInstance(org, module), "ivy", "ivy", "ivy"), RegexpPatternMatcher.INSTANCE, [:])
    }
}
