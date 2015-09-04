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
    def "accepts all modules default"() {
        def spec = DefaultModuleResolutionFilter.excludeAny()

        expect:
        spec.acceptModule(moduleId("org", "module"))
    }

    def "accepts all artifacts by default"() {
        def spec = DefaultModuleResolutionFilter.excludeAny()

        expect:
        spec.acceptArtifact(moduleId("org", "module"), artifactName("test", "jar", "jar"))
        spec.acceptsAllArtifacts()
    }

    def "default specs accept the same modules as each other"() {
        expect:
        DefaultModuleResolutionFilter.excludeAny().acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny())
    }

    @Unroll
    def "does not accept module that matches single module exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

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
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

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
    def "module exclude rule selects the same modules as itself (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)
        def same = DefaultModuleResolutionFilter.excludeAny(rule)
        def all = DefaultModuleResolutionFilter.excludeAny()
        def otherRule = DefaultModuleResolutionFilter.excludeAny(excludeRule('*', 'other'))
        def artifactRule = DefaultModuleResolutionFilter.excludeAny(excludeRule('*', 'other', 'thing', '*', '*'))

        then:
        spec.acceptsSameModulesAs(spec)
        spec.acceptsSameModulesAs(same)
        !spec.acceptsSameModulesAs(all)
        !spec.acceptsSameModulesAs(otherRule)
        !spec.acceptsSameModulesAs(artifactRule)

        where:
        rule << [excludeRule('*', '*'),
                 excludeRule('*', 'module'),
                 excludeRule('org', '*'),
                 excludeRule('org', 'module'),
                 regexpExcludeRule('or.*', "module"),
                 regexpExcludeRule('org', "mod.*")]
    }

    @Unroll
    def "accepts module for every artifact exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

        then:
        spec.acceptModule(moduleId('org', 'module'))
        !spec.acceptsAllArtifacts()

        where:
        rule << [excludeRule('*', '*', 'artifact'),
                 excludeRule('org', '*', 'artifact'),
                 excludeRule('org', 'module', 'artifact'),
                 regexpExcludeRule('.*', "m.*", 'artifact')]
    }

    @Unroll
    def "accepts artifact for every module exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

        then:
        spec.acceptArtifact(moduleId('org', 'module'), artifactName('name', 'jar', 'jar'))
        spec.acceptsAllArtifacts()

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
    def "does not accept artifact that matches single artifact exclude rule (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

        then:
        !spec.acceptArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))
        !spec.acceptsAllArtifacts()

        where:
        rule << [excludeRule('org', 'module', 'mylib', 'jar', 'jar'),
                 excludeRule('org', 'module', '*', 'jar', 'jar'),
                 excludeRule('org', 'module', 'mylib', '*', 'jar'),
                 excludeRule('org', 'module', 'mylib', 'jar', '*'),
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
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)

        then:
        spec.acceptArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))

        where:
        rule << [excludeRule('*', 'module', '*', '*', '*'),
                 excludeRule('org', '*', '*', '*', '*'),
                 excludeRule('org', 'module', '*', '*', '*'),
                 excludeRule('*', 'module2', '*', '*', '*'),
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

    @Unroll
    def "artifact exclude rule accepts the same modules as other rules that accept all modules (#rule)"() {
        when:
        def spec = DefaultModuleResolutionFilter.excludeAny(rule)
        def sameRule = DefaultModuleResolutionFilter.excludeAny(rule)
        def otherRule = DefaultModuleResolutionFilter.excludeAny(excludeRule('*', '*', 'thing', '*', '*'))
        def all = DefaultModuleResolutionFilter.all()
        def moduleRule = DefaultModuleResolutionFilter.excludeAny(excludeRule('*', 'module'))

        then:
        spec.acceptsSameModulesAs(spec)
        spec.acceptsSameModulesAs(sameRule)
        spec.acceptsSameModulesAs(otherRule)
        spec.acceptsSameModulesAs(all)
        all.acceptsSameModulesAs(spec)

        !spec.acceptsSameModulesAs(moduleRule)
        !moduleRule.acceptsSameModulesAs(spec)

        spec.acceptsSameModulesAs(spec.union(otherRule))
        spec.acceptsSameModulesAs(spec.union(moduleRule))
        spec.acceptsSameModulesAs(spec.intersect(otherRule.union(sameRule)))

        where:
        rule << [excludeRule('*', '*', '*', 'jar', 'jar'),
                 excludeRule('org', 'module', 'mylib', 'jar', 'jar'),
                 excludeRule('org', 'module', '*', 'jar', 'jar'),
                 excludeRule('org', 'module', 'mylib', '*', 'jar'),
                 excludeRule('org', 'module', 'mylib', 'jar', '*'),
                 regexpExcludeRule('org', "module", 'my.*', 'jar', 'jar'),
                 regexpExcludeRule('org', "module", 'mylib', 'j.*', 'jar'),
                 regexpExcludeRule('org', "module", 'mylib', 'jar', 'j.*')]
    }

    def "does not accept module version that matches any exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regexpExcludeRule("regexp-\\d+", "module\\d+")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4, rule5)

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
        def exactMatchSpec = DefaultModuleResolutionFilter.excludeAny(rule1)
        def moduleWildcard = DefaultModuleResolutionFilter.excludeAny(rule3)
        def groupWildcard = DefaultModuleResolutionFilter.excludeAny(rule4)
        def regexp = DefaultModuleResolutionFilter.excludeAny(rule5)
        def manyRules = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4, rule5)

        expect:
        exactMatchSpec.acceptsSameModulesAs(exactMatchSpec)
        exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1))

        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule2))
        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny())
        !exactMatchSpec.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2))

        moduleWildcard.acceptsSameModulesAs(moduleWildcard)
        moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule3))

        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1))
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule3))
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny())
        !moduleWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(excludeRule("org3", "*")))

        groupWildcard.acceptsSameModulesAs(groupWildcard)
        groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule4))

        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1))
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule4))
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny())
        !groupWildcard.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(excludeRule("*", "module5")))

        regexp.acceptsSameModulesAs(regexp)
        regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule5))

        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1))
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule5))
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny())
        !regexp.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(regexpExcludeRule("pattern", "other")))

        manyRules.acceptsSameModulesAs(manyRules)
        manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4, rule5))

        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4))

        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, excludeRule("org", "module3"), rule3, rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, excludeRule("org3", "*"), rule4, rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, excludeRule("*", "module5"), rule5))
        !manyRules.acceptsSameModulesAs(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4, regexpExcludeRule("other", "other")))
    }

    def "union with empty spec is empty spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny()

        expect:
        spec.union(spec2) == spec2
        spec2.union(spec) == spec2
    }

    def "union of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("a", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3)

        expect:
        spec.union(spec) == spec
    }

    def "union of two specs with the same exclude rule instances returns one of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org", "*")
        def rule4 = excludeRule("*", "module")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2, rule3, rule1, rule4)

        expect:
        spec.union(spec2) == spec
    }

    def "union of two specs with exact matching exclude rules uses the intersection of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org", "module3")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule1, rule3)

        expect:
        def union = spec.union(spec2)
        union == DefaultModuleResolutionFilter.excludeAny(rule1)
    }

    def "union of spec with module wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("org", "*")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("*", "module3")
        def rule6 = excludeRule("org2", "*")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1)

        expect:
        def union = spec.union(DefaultModuleResolutionFilter.excludeAny(rule2, rule3, rule4))
        union == DefaultModuleResolutionFilter.excludeAny(rule2, rule3)

        def union2 = spec.union(DefaultModuleResolutionFilter.excludeAny(rule5))
        union2 == DefaultModuleResolutionFilter.excludeAny(excludeRule("org", "module3"))

        def union3 = spec.union(DefaultModuleResolutionFilter.excludeAny(rule6, rule2))
        union3 == DefaultModuleResolutionFilter.excludeAny(rule2)
    }

    def "union of spec with group wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("*", "module")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("org", "*")
        def rule6 = excludeRule("*", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1)

        expect:
        def union = spec.union(DefaultModuleResolutionFilter.excludeAny(rule2, rule3, rule4))
        union == DefaultModuleResolutionFilter.excludeAny(rule2, rule4)

        def union2 = spec.union(DefaultModuleResolutionFilter.excludeAny(rule5))
        union2 == DefaultModuleResolutionFilter.excludeAny(excludeRule("org", "module"))

        def union3 = spec.union(DefaultModuleResolutionFilter.excludeAny(rule6))
        union3 == DefaultModuleResolutionFilter.all()
    }

    def "union of two specs with disjoint exact matching exclude rules excludes no modules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2)

        expect:
        def union = spec.union(spec2)
        union == DefaultModuleResolutionFilter.all()
    }

    def "union of a spec with exclude-all spec returns the original spec"() {
        def rule1 = excludeRule("*", "*")
        def rule2 = excludeRule("org", "module2")
        def spec1 = DefaultModuleResolutionFilter.excludeAny(rule1)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2)

        expect:
        spec1.union(spec2) == spec2
        spec2.union(spec1) == spec2
    }

    def "union of module spec and artifact spec uses the artifact spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("*", "module-2")
        def rule3 = excludeRule("org", "*-2")
        def artifactRule1 = excludeRule("org", "module", "art", "*", "*")
        def artifactRule2 = excludeRule("*", "*", "*", "jar", "*")
        def artifactSpec1 = DefaultModuleResolutionFilter.excludeAny(artifactRule1)
        def artifactSpec2 = DefaultModuleResolutionFilter.excludeAny(artifactRule1, artifactRule2)

        expect:
        def union = artifactSpec1.union(DefaultModuleResolutionFilter.excludeAny(rule1))
        union == artifactSpec1

        def union2 = artifactSpec1.union(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3))
        union2 == artifactSpec1

        def union3 = artifactSpec2.union(DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3))
        union3 == artifactSpec2
    }

    def "union of two specs with non-exact matching exclude rules is a union spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2)

        expect:
        def union = spec.union(spec2)
        def specs = []
        union.unpackUnion(specs)
        specs.size() == 2
        specs[0] == spec
        specs[1] == spec2
    }

    def "union of union specs is the union of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec3 = DefaultModuleResolutionFilter.excludeAny(rule3)

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
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule1)

        expect:
        def union = spec.union(spec2)

        !spec.acceptModule(moduleId("org", "module"))
        !union.acceptModule(moduleId("org", "module"))

        !spec.acceptModule(moduleId("org", "module2"))
        union.acceptModule(moduleId("org", "module2"))
    }

    def "union accepts artifact that is accepted by any merged exclude rule"() {
        def moduleId = moduleId("org", "module")
        def excludeA = excludeRule("org", "module", "a")
        def excludeB = excludeRule("org", "module", "b")
        def spec = DefaultModuleResolutionFilter.excludeAny(excludeA)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(excludeB)

        when:
        def union = spec.union(spec2)

        then:
        !union.acceptArtifact(moduleId, artifactName("a", "zip", "zip"))
        union.acceptArtifact(moduleId, artifactName("b", "zip", "zip"))
        union.acceptArtifact(moduleId, artifactName("c", "zip", "zip"))

        !union.acceptsAllArtifacts()
    }

    def "unions accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = DefaultModuleResolutionFilter.excludeAny(rule1)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2)
        def spec3 = DefaultModuleResolutionFilter.excludeAny(rule3)

        expect:
        spec1.union(spec2).acceptsSameModulesAs(spec2.union(spec1))

        !spec1.union(spec2).acceptsSameModulesAs(spec2)
        !spec1.union(spec2).acceptsSameModulesAs(spec1)
        !spec1.union(spec2).acceptsSameModulesAs(spec1.union(spec3))
    }

    def "intersection with empty spec is original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny()

        expect:
        spec.intersect(spec2) == spec
        spec2.intersect(spec) == spec
    }

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("b", "jar", "jar")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3)

        expect:
        spec.intersect(spec) == spec
    }

    def "intersection does not accept module that is not accepted by any merged exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule1)

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

    def "intersection accepts artifact that is accepted by every merged exclude rule"() {
        def moduleId = moduleId("org", "module")
        def excludeA = excludeRule("org", "module", "a")
        def excludeB = excludeRule("org", "module", "b")
        def spec = DefaultModuleResolutionFilter.excludeAny(excludeA, excludeB)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(excludeA)

        expect:
        def intersect = spec.intersect(spec2)

        !intersect.acceptArtifact(moduleId, artifactName("a", "zip", "zip"))
        !intersect.acceptArtifact(moduleId, artifactName("b", "zip", "zip"))
        intersect.acceptArtifact(moduleId, artifactName("c", "zip", "zip"))

        !intersect.acceptsAllArtifacts()
    }

    def "intersection of two specs with exclude rules is the union of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule1)

        expect:
        def intersection = spec.intersect(spec2)
        intersection == DefaultModuleResolutionFilter.excludeAny(rule1, rule2)
    }

    def "intersections accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = DefaultModuleResolutionFilter.excludeAny(rule1).union(DefaultModuleResolutionFilter.excludeAny(rule2))
        def spec2 = DefaultModuleResolutionFilter.excludeAny(rule2).union(DefaultModuleResolutionFilter.excludeAny(rule1))
        def spec3 = DefaultModuleResolutionFilter.excludeAny(rule3)
        assert spec1.acceptsSameModulesAs(spec2)

        expect:
        spec1.intersect(spec2).acceptsSameModulesAs(spec2.intersect(spec1))

        !spec1.intersect(spec2).acceptsSameModulesAs(spec1)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec2)
        !spec1.intersect(spec2).acceptsSameModulesAs(spec1.intersect(spec3))
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
        def spec = DefaultModuleResolutionFilter.excludeAny(rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8)

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
