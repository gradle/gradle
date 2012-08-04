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
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regExpExcludeRule("regexp-\\d+", "module\\d+")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module"))
        !spec.isSatisfiedBy(ModuleId.newInstance("org", "module2"))
        !spec.isSatisfiedBy(ModuleId.newInstance("org2", "anything"))
        !spec.isSatisfiedBy(ModuleId.newInstance("other", "module4"))
        !spec.isSatisfiedBy(ModuleId.newInstance("regexp-72", "module12"))
        spec.isSatisfiedBy(ModuleId.newInstance("org", "other"))
        spec.isSatisfiedBy(ModuleId.newInstance("regexp-72", "other"))
        spec.isSatisfiedBy(ModuleId.newInstance("regexp", "module2"))
    }

    def "specs with the same set of exclude rules accept the same modules as each other"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regExpExcludeRule("pattern1", "pattern2")
        def exactMatchSpec = ModuleVersionSpec.forExcludes(rule1)
        def moduleWildcard = ModuleVersionSpec.forExcludes(rule3)
        def groupWildcard = ModuleVersionSpec.forExcludes(rule4)
        def regexp = ModuleVersionSpec.forExcludes(rule5)
        def manyRules = ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        exactMatchSpec.acceptsSameModulesAs(exactMatchSpec)
        exactMatchSpec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1))

        !exactMatchSpec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule2))
        !exactMatchSpec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
        !exactMatchSpec.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2))

        moduleWildcard.acceptsSameModulesAs(moduleWildcard)
        moduleWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule3))

        !moduleWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1))
        !moduleWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule3))
        !moduleWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
        !moduleWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(excludeRule("org3", "*")))

        groupWildcard.acceptsSameModulesAs(groupWildcard)
        groupWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule4))

        !groupWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1))
        !groupWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule4))
        !groupWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
        !groupWildcard.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(excludeRule("*", "module5")))

        regexp.acceptsSameModulesAs(regexp)
        regexp.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule5))

        !regexp.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1))
        !regexp.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule5))
        !regexp.acceptsSameModulesAs(ModuleVersionSpec.forExcludes())
        !regexp.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(regExpExcludeRule("pattern", "other")))

        manyRules.acceptsSameModulesAs(manyRules)
        manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4, rule5))

        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule4, rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4))

        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, excludeRule("org", "module3"), rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, excludeRule("org3", "*"), rule4, rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule3, excludeRule("*", "module5"), rule5))
        !manyRules.acceptsSameModulesAs(ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4, regExpExcludeRule("other", "other")))
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
        def rule3 = excludeRule("org", "*")
        def rule4 = excludeRule("*", "module")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2, rule3, rule4)
        def spec2 = ModuleVersionSpec.forExcludes(rule2, rule3, rule1, rule4)

        expect:
        spec.union(spec2) == spec
    }

    def "union of two specs with exact matching exclude rules uses the intersection of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org", "module3")
        def spec = ModuleVersionSpec.forExcludes(rule1, rule2)
        def spec2 = ModuleVersionSpec.forExcludes(rule1, rule3)

        expect:
        def union = spec.union(spec2)
        union instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 1
        union.excludeSpecs.any { it.moduleId == rule1.id.moduleId }
    }

    def "union of spec with module wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("org", "*")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("*", "module3")
        def rule6 = excludeRule("org2", "*")
        def spec = ModuleVersionSpec.forExcludes(rule1)

        expect:
        def union = spec.union(ModuleVersionSpec.forExcludes(rule2, rule3, rule4))
        union instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 2
        union.excludeSpecs.any { it.moduleId == rule2.id.moduleId }
        union.excludeSpecs.any { it.moduleId == rule3.id.moduleId }
        
        def union2 = spec.union(ModuleVersionSpec.forExcludes(rule5))
        union2 instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union2.excludeSpecs.size() == 1
        union2.excludeSpecs.any { it.moduleId.organisation == 'org' && it.moduleId.name == 'module3' }

        def union3 = spec.union(ModuleVersionSpec.forExcludes(rule6, rule2))
        union3 instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union3.excludeSpecs.size() == 1
        union3.excludeSpecs.any { it.moduleId == rule2.id.moduleId }
    }

    def "union of spec with group wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("*", "module")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("org", "*")
        def rule6 = excludeRule("*", "module2")
        def spec = ModuleVersionSpec.forExcludes(rule1)

        expect:
        def union = spec.union(ModuleVersionSpec.forExcludes(rule2, rule3, rule4))
        union instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 2
        union.excludeSpecs.any { it.moduleId == rule2.id.moduleId }
        union.excludeSpecs.any { it.moduleId == rule4.id.moduleId }

        def union2 = spec.union(ModuleVersionSpec.forExcludes(rule5))
        union2 instanceof ModuleVersionSpec.ExcludeRuleBackedSpec
        union2.excludeSpecs.size() == 1
        union2.excludeSpecs.any { it.moduleId.organisation == 'org' && it.moduleId.name == 'module' }

        def union3 = spec.union(ModuleVersionSpec.forExcludes(rule6))
        union3 == ModuleVersionSpec.forExcludes()
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
        def union = spec.union(spec3).union(spec2)

        union instanceof ModuleVersionSpec.UnionSpec
        union.specs.size() == 2
        union.specs.any {
            it instanceof ModuleVersionSpec.ExcludeRuleBackedSpec && it.excludeSpecs == spec.excludeSpecs
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
        intersection.excludeSpecs.size() == 2
        intersection.excludeSpecs.any { it.moduleId == rule1.id.moduleId }
        intersection.excludeSpecs.any { it.moduleId == rule2.id.moduleId }
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
