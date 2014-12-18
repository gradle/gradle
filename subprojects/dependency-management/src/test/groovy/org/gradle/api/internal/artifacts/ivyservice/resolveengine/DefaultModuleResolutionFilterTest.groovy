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
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.RegexpPatternMatcher
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification
import spock.lang.Unroll

class DefaultModuleResolutionFilterTest extends Specification {
    def "accepts all modules and artifacts by default"() {
        def spec = DefaultModuleResolutionFilter.forExcludes()

        expect:
        spec.acceptModule(moduleId("org", "module"))
    }

    def "accepts all artifacts by default"() {
        def spec = DefaultModuleResolutionFilter.forExcludes()

        expect:
        spec.acceptArtifact(moduleId("org", "module"), artifactName("test", "jar", "jar"))
    }

    def "default specs accept the same modules as each other"() {
        expect:
        DefaultModuleResolutionFilter.forExcludes().acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes())
    }

    @Unroll
    def "does not accept module that matches single module exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.forExcludes(rule)

        then:
        !spec.acceptModule(moduleId('org', 'module'))

        where:
        rule << [excludeRule('*', '*'),
                 excludeRule('org', 'module'),
                 excludeRule('org', '*'),
                 excludeRule('*', 'module'),
                 regexpExcludeRule('*', '*'),
                 regexpExcludeRule('or.*', 'module'),
                 regexpExcludeRule('org', 'mod.*'),
                 regexpExcludeRule('or.*', '*'),
                 regexpExcludeRule('*', 'mod.*')]
    }

    @Unroll
    def "accepts module that doesn't match single module exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.forExcludes(rule)

        then:
        spec.acceptModule(moduleId('org', 'module'))

        where:
        rule << [excludeRule('org2', 'module2'),
                 excludeRule('*', 'module2'),
                 excludeRule('org2', '*'),
                 regexpExcludeRule('or.*2', "module"),
                 regexpExcludeRule('org', "mod.*2"),
                 regexpExcludeRule('or.*2', "*"),
                 regexpExcludeRule('*', "mod.*2")]
    }

    @Unroll
    def "does not accept artifact that matches single artifact exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.forExcludes(rule)

        then:
        !spec.acceptArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))

        where:
        rule << [excludeRule('*', '*', '*', '*', '*'),
                 excludeRule('org', '*', '*', '*', '*'),
                 excludeRule('*', 'module', '*', '*', '*'),
                 excludeRule('org', 'module', '*', '*', '*'),
                 excludeRule('org', 'module', 'mylib', 'jar', 'jar'),
                 excludeRule('org', 'module', '*', 'jar', 'jar'),
                 excludeRule('org', 'module', 'mylib', '*', 'jar'),
                 excludeRule('org', 'module', 'mylib', 'jar', '*'),
                 regexpExcludeRule('*', '*', '*', '*', '*'),
                 regexpExcludeRule('or.*', '*', '*', '*', '*'),
                 regexpExcludeRule('or.*', 'module', '*', '*', '*'),
                 regexpExcludeRule('*', 'mod.*', '*', '*', '*'),
                 regexpExcludeRule('org', 'mod.*', '*', '*', '*'),
                 regexpExcludeRule('org', 'module', 'my.*', 'jar', 'jar'),
                 regexpExcludeRule('org', 'module', 'my.*', '*', '*'),
                 regexpExcludeRule('org', 'module', 'mylib', 'j.*', 'jar'),
                 regexpExcludeRule('org', 'module', '*', 'j.*', 'jar'),
                 regexpExcludeRule('org', 'module', 'mylib', 'jar', 'j.*'),
                 regexpExcludeRule('org', 'module', 'mylib', '*', 'j.*')]
    }

    @Unroll
    def "accepts artifact that doesn't match single artifact exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.forExcludes(rule)

        then:
        spec.acceptArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))

        where:
        rule << [excludeRule('*', 'module2', '*', '*', '*'),
                 excludeRule('org2', '*', '*', '*', '*'),
                 excludeRule('org2', 'module2', '*', '*', '*'),
                 excludeRule('org', 'module', 'mylib', 'sources', 'jar'),
                 excludeRule('org', 'module', 'mylib', 'jar', 'war'),
                 excludeRule('org', 'module', 'otherlib', 'jar', 'jar'),
                 excludeRule('org', 'module', 'otherlib', '*', '*'),
                 excludeRule('org', 'module', 'otherlib', '*', 'jar'),
                 excludeRule('org', 'module', 'otherlib', 'jar', '*'),
                 excludeRule('org', 'module', '*', 'sources', 'jar'),
                 excludeRule('org', 'module', '*', 'sources', '*'),
                 excludeArtifactRule('mylib', 'sources', 'jar'),
                 excludeArtifactRule('mylib', 'jar', 'war'),
                 excludeArtifactRule('otherlib', 'jar', 'jar'),
                 excludeArtifactRule('otherlib', '*', '*'),
                 excludeArtifactRule('*', 'sources', 'jar'),
                 excludeArtifactRule('otherlib', '*', 'jar'),
                 excludeArtifactRule('otherlib', 'jar', '*'),
                 regexpExcludeRule('or.*2', 'module', '*', '*', '*'),
                 regexpExcludeRule('org', 'mod.*2', '*', '*', '*'),
                 regexpExcludeRule('org', 'module', 'my.*2', '*', '*'),
                 regexpExcludeRule('org', 'module', 'mylib', 'j.*2', '*'),
                 regexpExcludeRule('org', 'module', 'mylib', 'jar', 'j.*2'),
                 regexpExcludeArtifactRule('my.*2', '*', '*'),
                 regexpExcludeArtifactRule('mylib', 'j.*2', '*'),
                 regexpExcludeArtifactRule('mylib', 'jar', 'j.*2')]
    }

    def "does not accept module version that matches any exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regexpExcludeRule("regexp-\\d+", "module\\d+")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        !spec.acceptModule(moduleId("org", "module"))
        !spec.acceptModule(moduleId("org", "module2"))
        !spec.acceptModule(moduleId("org2", "anything"))
        !spec.acceptModule(moduleId("other", "module4"))
        !spec.acceptModule(moduleId("regexp-72", "module12"))
        spec.acceptModule(moduleId("org", "other"))
        spec.acceptModule(moduleId("regexp-72", "other"))
        spec.acceptModule(moduleId("regexp", "module2"))
    }

    def "specs with the same set of exclude rules accept the same modules as each other"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regexpExcludeRule("pattern1", "pattern2")
        def exactMatchSpec = DefaultModuleResolutionFilter.forExcludes(rule1)
        def moduleWildcard = DefaultModuleResolutionFilter.forExcludes(rule3)
        def groupWildcard = DefaultModuleResolutionFilter.forExcludes(rule4)
        def regexp = DefaultModuleResolutionFilter.forExcludes(rule5)
        def manyRules = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        exactMatchSpec.acceptsSameModulesAs(exactMatchSpec)
        exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1))

        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule2))
        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes())
        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2))

        moduleWildcard.acceptsSameModulesAs(moduleWildcard)
        moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule3))

        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1))
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule3))
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes())
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(excludeRule("org3", "*")))

        groupWildcard.acceptsSameModulesAs(groupWildcard)
        groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule4))

        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1))
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule4))
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes())
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(excludeRule("*", "module5")))

        regexp.acceptsSameModulesAs(regexp)
        regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule5))

        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1))
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule5))
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes())
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(regexpExcludeRule("pattern", "other")))

        manyRules.acceptsSameModulesAs(manyRules)
        manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, rule5))

        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4))

        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, excludeRule("org", "module3"), rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, excludeRule("org3", "*"), rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, excludeRule("*", "module5"), rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, regexpExcludeRule("other", "other")))
    }

    def "union with empty spec is empty spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes()

        expect:
        spec.union(spec2) == spec2
        spec2.union(spec) == spec2
    }

    def "union of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("a", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3)

        expect:
        spec.union(spec) == spec
    }

    def "union of two specs with the same exclude rule instances returns one of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org", "*")
        def rule4 = excludeRule("*", "module")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule2, rule3, rule1, rule4)

        expect:
        spec.union(spec2) == spec
    }

    def "union of two specs with exact matching exclude rules uses the intersection of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org", "module3")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule1, rule3)

        expect:
        def union = spec.union(spec2)
        union instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 1
        union.excludeSpecs.any { specForRule(it, rule1) }
    }

    def "union of spec with module wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("org", "*")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("*", "module3")
        def rule6 = excludeRule("org2", "*")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1)

        expect:
        def union = spec.union(DefaultModuleResolutionFilter.forExcludes(rule2, rule3, rule4))
        union instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 2
        union.excludeSpecs.any { specForRule(it, rule2) }
        union.excludeSpecs.any { specForRule(it, rule3) }
        
        def union2 = spec.union(DefaultModuleResolutionFilter.forExcludes(rule5))
        union2 instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union2.excludeSpecs.size() == 1
        union2.excludeSpecs.any { it.moduleId.group == 'org' && it.moduleId.name == 'module3' }

        def union3 = spec.union(DefaultModuleResolutionFilter.forExcludes(rule6, rule2))
        union3 instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union3.excludeSpecs.size() == 1
        union3.excludeSpecs.any { specForRule(it, rule2) }
    }

    def "union of spec with group wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("*", "module")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("org", "*")
        def rule6 = excludeRule("*", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1)

        expect:
        def union = spec.union(DefaultModuleResolutionFilter.forExcludes(rule2, rule3, rule4))
        union instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union.excludeSpecs.size() == 2
        union.excludeSpecs.any { specForRule(it, rule2) }
        union.excludeSpecs.any { specForRule(it, rule4) }

        def union2 = spec.union(DefaultModuleResolutionFilter.forExcludes(rule5))
        union2 instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        union2.excludeSpecs.size() == 1
        union2.excludeSpecs.any { it.moduleId.group == 'org' && it.moduleId.name == 'module' }

        def union3 = spec.union(DefaultModuleResolutionFilter.forExcludes(rule6))
        union3 == DefaultModuleResolutionFilter.forExcludes()
    }

    def "union of two specs with disjoint exact matching exclude rules matches all modules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule2)

        expect:
        def union = spec.union(spec2)
        union == DefaultModuleResolutionFilter.forExcludes()
    }

    def "union of two specs with non-exact matching exclude rules is a union spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule2)

        expect:
        def union = spec.union(spec2)
        union instanceof DefaultModuleResolutionFilter.UnionSpec
        union.specs.size() == 2
        union.specs[0] == spec
        union.specs[1] == spec2
    }

    def "union of union specs is the union of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec3 = DefaultModuleResolutionFilter.forExcludes(rule3)

        expect:
        def union = spec.union(spec3).union(spec2)

        union instanceof DefaultModuleResolutionFilter.UnionSpec
        union.specs.size() == 2
        union.specs.any {
            it instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec && it.excludeSpecs == spec.excludeSpecs
        }
        union.specs.contains(spec3)
    }

    def "union accept module that is accepted by any merged exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule1)

        expect:
        def union = spec.union(spec2)

        !spec.acceptModule(moduleId("org", "module"))
        !union.acceptModule(moduleId("org", "module"))

        !spec.acceptModule(moduleId("org", "module2"))
        union.acceptModule(moduleId("org", "module2"))
    }

    def "unions accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = DefaultModuleResolutionFilter.forExcludes(rule1)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule2)
        def spec3 = DefaultModuleResolutionFilter.forExcludes(rule3)

        expect:
        spec1.union(spec2).acceptsSameModulesAs(spec2.union(spec1))

        !spec1.union(spec2).acceptsSameModulesAs(spec2)
        !spec1.union(spec2).acceptsSameModulesAs(spec1)
        !spec1.union(spec2).acceptsSameModulesAs(spec1.union(spec3))
    }

    def "intersection with empty spec is original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes()

        expect:
        spec.intersect(spec2) == spec
        spec2.intersect(spec) == spec
    }

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3)

        expect:
        spec.intersect(spec) == spec
    }

    def "intersection does not accept module that is not accepted by any merged exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule1)

        expect:
        def intersect = spec.intersect(spec2)

        !spec.acceptModule(moduleId("org", "module"))
        !intersect.acceptModule(moduleId("org", "module"))

        !spec.acceptModule(moduleId("org", "module2"))
        !intersect.acceptModule(moduleId("org", "module2"))

        spec.acceptModule(moduleId("org", "module3"))
        spec2.acceptModule(moduleId("org", "module3"))
        intersect.acceptModule(moduleId("org", "module3"))
    }

    def "intersection of two specs with exclude rules is the union of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule1)

        expect:
        def intersection = spec.intersect(spec2)
        intersection instanceof DefaultModuleResolutionFilter.ExcludeRuleBackedSpec
        intersection.excludeSpecs.size() == 2
        intersection.excludeSpecs.any { specForRule(it, rule1) }
        intersection.excludeSpecs.any { specForRule(it, rule2) }
    }

    def "intersections accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = DefaultModuleResolutionFilter.forExcludes(rule1).union(DefaultModuleResolutionFilter.forExcludes(rule2))
        def spec2 = DefaultModuleResolutionFilter.forExcludes(rule2).union(DefaultModuleResolutionFilter.forExcludes(rule1))
        def spec3 = DefaultModuleResolutionFilter.forExcludes(rule3)
        assert spec1.acceptsSameModulesAs(spec2)

        expect:
        spec1.intersect(spec2).acceptsSameModulesAs(spec2.intersect(spec1))

        !spec1.intersect(spec2).acceptsSameModulesAs(spec1)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec2)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec1.intersect(spec3))
    }

    def "does not accept artifact that matches any exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regexpExcludeRule("regexp-\\d+", "module\\d+")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, rule5)

        expect:
        !spec.acceptArtifact(moduleId("org", "module"), artifactName("a", "jar", "jar"))
        !spec.acceptArtifact(moduleId("org", "module2"), artifactName("b", "jar", "jar"))
        !spec.acceptArtifact(moduleId("org2", "anything"), artifactName("c", "jar", "jar"))
        !spec.acceptArtifact(moduleId("other", "module4"), artifactName("d", "jar", "jar"))
        !spec.acceptArtifact(moduleId("regexp-72", "module12"), artifactName("e", "jar", "jar"))
        spec.acceptArtifact(moduleId("org", "other"), artifactName("f", "jar", "jar"))
        spec.acceptArtifact(moduleId("regexp-72", "other"), artifactName("g", "jar", "jar"))
        spec.acceptArtifact(moduleId("regexp", "module2"), artifactName("h", "jar", "jar"))
    }

    def "does not accept artifact that matches specific exclude rule"() {
        def rule1 = excludeArtifactRule("a", "jar", "jar")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def rule3 = excludeArtifactRule("c", "*", "*")
        def rule4 = excludeArtifactRule("d", "*", "jar")
        def rule5 = excludeArtifactRule("e", "sources", "jar")
        def rule6 = excludeArtifactRule("f", "sources", "*")
        def rule7 = excludeArtifactRule("g", "jar", "war")
        def rule8 = regexpExcludeArtifactRule("regexp-\\d+", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.forExcludes(rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8)

        expect:
        !spec.acceptArtifact(moduleId("org", "module"), artifactName("a", "jar", "jar"))
        !spec.acceptArtifact(moduleId("org", "module2"), artifactName("b", "jar", "jar"))
        !spec.acceptArtifact(moduleId("org2", "anything"), artifactName("c", "jar", "jar"))
        !spec.acceptArtifact(moduleId("other", "module4"), artifactName("d", "jar", "jar"))
        !spec.acceptArtifact(moduleId("some", "app"), artifactName("e", "sources", "jar"))
        !spec.acceptArtifact(moduleId("foo", "bar"), artifactName("f", "sources", "jar"))
        !spec.acceptArtifact(moduleId("well", "known"), artifactName("g", "jar", "war"))
        !spec.acceptArtifact(moduleId("other", "sample"), artifactName("regexp-99", "jar", "jar"))
        spec.acceptArtifact(moduleId("some", "app"), artifactName("e", "jar", "jar"))
        spec.acceptArtifact(moduleId("some", "app"), artifactName("e", "javadoc", "jar"))
        spec.acceptArtifact(moduleId("foo", "bar"), artifactName("f", "jar", "jar"))
        spec.acceptArtifact(moduleId("well", "known"), artifactName("g", "jar", "jar"))
        spec.acceptArtifact(moduleId("well", "known"), artifactName("g", "jar", "zip"))
        spec.acceptArtifact(moduleId("other", "sample"), artifactName("regexp", "jar", "jar"))
    }

    static specForRule(def spec, ExcludeRule rule) {
        return spec.moduleId.group == rule.id.moduleId.organisation && spec.moduleId.name == rule.id.moduleId.name
    }

    def moduleId(String group, String name) {
        return DefaultModuleIdentifier.newId(group, name);
    }

    def artifactName(String name, String type, String ext) {
        return new DefaultIvyArtifactName(name, type, ext)
    }

    def excludeRule(String org, String module, String name = "*", String type = "*", String ext = "*") {
        new DefaultExcludeRule(IvyUtil.createArtifactId(org, module, name, type, ext), ExactPatternMatcher.INSTANCE, [:])
    }

    def excludeArtifactRule(String name, String type, String ext) {
        excludeRule("*", "*", name, type, ext)
    }

    def regexpExcludeRule(String org, String module, String name = "*", String type = "*", String ext = "*") {
        new DefaultExcludeRule(IvyUtil.createArtifactId(org, module, name, type, ext), RegexpPatternMatcher.INSTANCE, [:])
    }

    def regexpExcludeArtifactRule(String name, String type, String ext) {
        regexpExcludeRule("*", "*", name, type, ext)
    }
}
