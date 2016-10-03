/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes

import groovy.transform.NotYetImplemented
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import spock.lang.Specification
import spock.lang.Unroll

import static ModuleExclusions.excludeAny
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions.excludeNone

class DefaultModuleExclusionTest extends Specification {
    def "accepts all modules default"() {
        def spec = excludeAny()

        expect:
        !spec.excludeModule(moduleId("org", "module"))
    }

    def "accepts all artifacts by default"() {
        def spec = excludeAny()

        expect:
        !spec.excludeArtifact(moduleId("org", "module"), artifactName("test", "jar", "jar"))
        !spec.mayExcludeArtifacts()
    }

    def "spec with no rules excludes nothing"() {
        expect:
        excludeAny().is(excludeNone())
    }

    def "default specs accept the same modules as each other"() {
        expect:
        excludeAny().excludesSameModulesAs(excludeAny())
        excludeNone().excludesSameModulesAs(excludeNone())
    }

    def "specs are equal when they contain the same rules"() {
        def rule1 = excludeRule("*", "*")
        def rule2 = excludeRule("org", "*")
        def rule3 = excludeRule("org", "module")

        expect:
        excludeAny(rule1) == excludeAny(rule1)
        excludeAny(rule1) != excludeAny(rule2)
        excludeAny(rule1) != excludeAny(rule1, rule2)

        excludeAny(rule2, rule1) == excludeAny(rule1, rule2)
        excludeAny(rule2, rule1) != excludeAny(rule1, rule3)
        excludeAny(rule2, rule1) != excludeAny(rule1, rule2, rule3)
    }

    @Unroll
    def "does not accept module that matches single module exclude rule (#rule)"() {
        when:
        def spec = excludeAny(rule)

        then:
        spec.excludeModule(moduleId('org', 'module'))

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
        def spec = excludeAny(rule)

        then:
        !spec.excludeModule(moduleId('org', 'module'))

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
        def spec = excludeAny(rule)
        def same = excludeAny(rule)
        def all = excludeAny()
        def otherRule = excludeAny(excludeRule('*', 'other'))
        def artifactRule = excludeAny(excludeRule('*', 'other', 'thing', '*', '*'))

        then:
        spec.excludesSameModulesAs(spec)
        spec.excludesSameModulesAs(same)
        !spec.excludesSameModulesAs(all)
        !spec.excludesSameModulesAs(otherRule)
        !spec.excludesSameModulesAs(artifactRule)

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
        def spec = excludeAny(rule)

        then:
        !spec.excludeModule(moduleId('org', 'module'))
        spec.mayExcludeArtifacts()

        where:
        rule << [excludeRule('*', '*', 'artifact'),
                 excludeRule('org', '*', 'artifact'),
                 excludeRule('org', 'module', 'artifact'),
                 regexpExcludeRule('.*', "m.*", 'artifact')]
    }

    @Unroll
    def "accepts artifact for every module exclude rule (#rule)"() {
        when:
        def spec = excludeAny(rule)

        then:
        !spec.excludeArtifact(moduleId('org', 'module'), artifactName('name', 'jar', 'jar'))
        !spec.mayExcludeArtifacts()

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
        def spec = excludeAny(rule)

        then:
        spec.excludeArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))
        spec.mayExcludeArtifacts()

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
        def spec = excludeAny(rule)

        then:
        !spec.excludeArtifact(moduleId('org', 'module'), artifactName('mylib', 'jar', 'jar'))

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
        def spec = ModuleExclusions.excludeAny(rule)
        def sameRule = ModuleExclusions.excludeAny(rule)
        def otherRule = excludeAny(excludeRule('*', '*', 'thing', '*', '*'))
        def all = excludeNone()
        def moduleRule = excludeAny(excludeRule('*', 'module'))

        then:
        spec.excludesSameModulesAs(spec)
        spec.excludesSameModulesAs(sameRule)
        spec.excludesSameModulesAs(otherRule)
        spec.excludesSameModulesAs(all)
        all.excludesSameModulesAs(spec)

        !spec.excludesSameModulesAs(moduleRule)
        !moduleRule.excludesSameModulesAs(spec)

        spec.excludesSameModulesAs(union(spec, otherRule))
        spec.excludesSameModulesAs(union(spec, moduleRule))
        spec.excludesSameModulesAs(intersect(spec, union(otherRule, sameRule)))

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
        def spec = excludeAny(rule1, rule2, rule3, rule4, rule5)

        expect:
        spec.excludeModule(moduleId("org", "module"))
        spec.excludeModule(moduleId("org", "module2"))
        spec.excludeModule(moduleId("org2", "anything"))
        spec.excludeModule(moduleId("other", "module4"))
        spec.excludeModule(moduleId("regexp-72", "module12"))
        !spec.excludeModule(moduleId("org", "other"))
        !spec.excludeModule(moduleId("regexp-72", "other"))
        !spec.excludeModule(moduleId("regexp", "module2"))
    }

    def "specs with the same set of exclude rules accept the same modules as each other"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module4")
        def rule5 = regexpExcludeRule("pattern1", "pattern2")
        def exactMatchSpec = excludeAny(rule1)
        def moduleWildcard = excludeAny(rule3)
        def groupWildcard = excludeAny(rule4)
        def regexp = excludeAny(rule5)
        def manyRules = excludeAny(rule1, rule2, rule3, rule4, rule5)

        expect:
        exactMatchSpec.excludesSameModulesAs(exactMatchSpec)
        exactMatchSpec.excludesSameModulesAs(excludeAny(rule1))

        !exactMatchSpec.excludesSameModulesAs(excludeAny(rule2))
        !exactMatchSpec.excludesSameModulesAs(excludeAny())
        !exactMatchSpec.excludesSameModulesAs(excludeAny(rule1, rule2))

        moduleWildcard.excludesSameModulesAs(moduleWildcard)
        moduleWildcard.excludesSameModulesAs(excludeAny(rule3))

        !moduleWildcard.excludesSameModulesAs(excludeAny(rule1))
        !moduleWildcard.excludesSameModulesAs(excludeAny(rule1, rule3))
        !moduleWildcard.excludesSameModulesAs(excludeAny())
        !moduleWildcard.excludesSameModulesAs(excludeAny(excludeRule("org3", "*")))

        groupWildcard.excludesSameModulesAs(groupWildcard)
        groupWildcard.excludesSameModulesAs(excludeAny(rule4))

        !groupWildcard.excludesSameModulesAs(excludeAny(rule1))
        !groupWildcard.excludesSameModulesAs(excludeAny(rule1, rule4))
        !groupWildcard.excludesSameModulesAs(excludeAny())
        !groupWildcard.excludesSameModulesAs(excludeAny(excludeRule("*", "module5")))

        regexp.excludesSameModulesAs(regexp)
        regexp.excludesSameModulesAs(excludeAny(rule5))

        !regexp.excludesSameModulesAs(excludeAny(rule1))
        !regexp.excludesSameModulesAs(excludeAny(rule1, rule5))
        !regexp.excludesSameModulesAs(excludeAny())
        !regexp.excludesSameModulesAs(excludeAny(regexpExcludeRule("pattern", "other")))

        manyRules.excludesSameModulesAs(manyRules)
        manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule3, rule4, rule5))

        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule3, rule4, rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule4, rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule3, rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule3, rule4))

        !manyRules.excludesSameModulesAs(excludeAny(rule1, excludeRule("org", "module3"), rule3, rule4, rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, excludeRule("org3", "*"), rule4, rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule3, excludeRule("*", "module5"), rule5))
        !manyRules.excludesSameModulesAs(excludeAny(rule1, rule2, rule3, rule4, regexpExcludeRule("other", "other")))
    }

    def "union with empty spec is empty spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeNone()

        expect:
        union(spec, spec2).is(spec2)
        union(spec2, spec).is(spec2)
    }

    def "union of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("a", "jar", "jar")
        def spec = excludeAny(rule1, rule2, rule3)

        expect:
        union(spec, spec).is(spec)
    }

    def "union of two specs with the same exclude rule instances returns one of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module3")
        def spec = excludeAny(rule1, rule2, rule3, rule4)
        def spec2 = excludeAny(rule2, rule3, rule1, rule4)

        expect:
        union(spec, spec2).is(spec)
    }

    @NotYetImplemented
    def "union of two specs where one spec contains a superset of rules returns the spec with the subset of rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module3")
        def spec = excludeAny(rule1, rule2, rule3, rule4)
        def spec2 = excludeAny(rule2, rule1, rule4)

        expect:
        union(spec, spec2).is(spec2)
        union(spec2, spec).is(spec2)
    }

    def "union of two specs with exact matching exclude rules uses the intersection of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeRule("org", "module3")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeAny(rule1, rule3)

        expect:
        def union = union(spec, spec2)
        union == excludeAny(rule1)
    }

    def "union of spec with module wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("org", "*")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("*", "module3")
        def rule6 = excludeRule("org2", "*")
        def spec = excludeAny(rule1)

        expect:
        def union1 = union(spec, excludeAny(rule2, rule3, rule4))
        union1 == excludeAny(rule2, rule3)

        def union2 = union(spec, excludeAny(rule5))
        union2 == excludeAny(excludeRule("org", "module3"))

        def union3 = union(spec, excludeAny(rule6, rule2))
        union3 == excludeAny(rule2)
    }

    def "union of spec with group wildcard uses the most specific matching exclude rules"() {
        def rule1 = excludeRule("*", "module")
        def rule2 = excludeRule("org", "module")
        def rule3 = excludeRule("org", "module2")
        def rule4 = excludeRule("other", "module")
        def rule5 = excludeRule("org", "*")
        def rule6 = excludeRule("*", "module2")
        def spec = excludeAny(rule1)

        expect:
        def union1 = union(spec, excludeAny(rule2, rule3, rule4))
        union1 == excludeAny(rule2, rule4)

        def union2 = union(spec, excludeAny(rule5))
        union2 == excludeAny(excludeRule("org", "module"))

        def union3 = union(spec, excludeAny(rule6))
        union3 == excludeNone()
    }

    def "union of two specs with disjoint exact matching exclude rules excludes no modules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = excludeAny(rule1)
        def spec2 = excludeAny(rule2)

        expect:
        def union = union(spec, spec2)
        union == excludeNone()
    }

    def "union of a spec with exclude-all spec returns the original spec"() {
        def rule1 = excludeRule("*", "*")
        def rule2 = excludeRule("org", "module2")
        def spec1 = excludeAny(rule1)
        def spec2 = excludeAny(rule2)

        expect:
        union(spec1, spec2) == spec2
        union(spec2, spec1) == spec2
    }

    def "union of module spec and artifact spec uses the artifact spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("*", "module-2")
        def rule3 = excludeRule("org", "*-2")
        def artifactRule1 = excludeRule("org", "module", "art", "*", "*")
        def artifactRule2 = excludeRule("*", "*", "*", "jar", "*")
        def artifactSpec1 = excludeAny(artifactRule1)
        def artifactSpec2 = excludeAny(artifactRule1, artifactRule2)

        expect:
        def union1 = union(artifactSpec1, excludeAny(rule1))
        union1 == artifactSpec1

        def union2 = union(artifactSpec1, excludeAny(rule1, rule2, rule3))
        union2 == artifactSpec1

        def union3 = union(artifactSpec2, excludeAny(rule1, rule2, rule3))
        union3 == artifactSpec2
    }

    def "union of two specs with non-exact matching exclude rules is a union spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def spec = excludeAny(rule1)
        def spec2 = excludeAny(rule2)

        expect:
        def union = union(spec, spec2)
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
        def spec = excludeAny(rule1)
        def spec2 = excludeAny(rule1, rule2)
        def spec3 = excludeAny(rule3)

        expect:
        def union = union(union(spec, spec3), spec2)

        union instanceof UnionExclusion
        union.filters.size() == 2
        union.filters.any {
            it instanceof IntersectionExclusion && it.excludeSpecs == spec.excludeSpecs
        }
        union.filters.contains(spec3)
    }

    // Regression test for GRADLE-3275, also exercises GRADLE-3434
    def "intersection propagates through child union rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec = excludeAny(rule1)
        def spec2 = excludeAny(rule1, rule2)
        def spec3 = excludeAny(rule3)

        def excludeBacked1 = intersect(spec, spec2);         // module + module2
        def union1 = union(spec2, spec3);                     // module, module2, module3
        def excludeBacked2 = intersect(spec2, union1);        // module, module2
        def finalUnion = union(spec3, excludeBacked2);       // module

        expect:
        // Sanity checks.
        excludeBacked1 == spec2
        def specs = []
        union1.unpackUnion(specs)
        specs == [spec2, spec3];

        // Verify test is exercising the function it's supposed to.
        excludeBacked1 instanceof IntersectionExclusion
        excludeBacked2 instanceof IntersectionExclusion

        union1 instanceof UnionExclusion
        finalUnion instanceof UnionExclusion

        !spec2.excludeModule(moduleId("org", "module4"))

        // Verify that this function passes the intersection operation through to union2's rules.
        !finalUnion.excludeModule(moduleId("org", "module"))
        !finalUnion.excludeModule(moduleId("org", "module2"))
        !finalUnion.excludeModule(moduleId("org", "module3"))
    }

    def "union accept module that is accepted by any merged exclude rule"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeAny(rule1)

        expect:
        def union1 = union(spec, spec2)

        spec.excludeModule(moduleId("org", "module"))
        union1.excludeModule(moduleId("org", "module"))

        spec.excludeModule(moduleId("org", "module2"))
        !union1.excludeModule(moduleId("org", "module2"))
    }

    def "union accepts artifact that is accepted by any merged exclude rule"() {
        def moduleId = moduleId("org", "module")
        def excludeA = excludeRule("org", "module", "a")
        def excludeB = excludeRule("org", "module", "b")
        def spec = excludeAny(excludeA)
        def spec2 = excludeAny(excludeB)

        when:
        def union1 = union(spec, spec2)

        then:
        union1.excludeArtifact(moduleId, artifactName("a", "zip", "zip"))
        !union1.excludeArtifact(moduleId, artifactName("b", "zip", "zip"))
        !union1.excludeArtifact(moduleId, artifactName("c", "zip", "zip"))

        union1.mayExcludeArtifacts()
    }

    def "unions accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = excludeAny(rule1)
        def spec2 = excludeAny(rule2)
        def spec3 = excludeAny(rule3)

        expect:
        union(spec1, spec2).excludesSameModulesAs(union(spec2, spec1))

        !union(spec1, spec2).excludesSameModulesAs(spec2)
        !union(spec1, spec2).excludesSameModulesAs(spec1)
        !union(spec1, spec2).excludesSameModulesAs(union(spec1, spec3))
    }

    def "intersection with empty spec is original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeArtifactRule("b", "jar", "jar")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeNone()

        expect:
        intersect(spec, spec2).is(spec)
        intersect(spec2, spec).is(spec)
    }

    def "intersection of a spec with itself returns the original spec"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def rule3 = excludeArtifactRule("b", "jar", "jar")
        def spec = excludeAny(rule1, rule2, rule3)

        expect:
        intersect(spec, spec).is(spec)
    }

    def "intersection of two specs with the same exclude rule instances returns one of the original specs"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module3")
        def spec = excludeAny(rule1, rule2, rule3, rule4)
        def spec2 = excludeAny(rule2, rule3, rule1, rule4)

        expect:
        intersect(spec, spec2).is(spec)
    }

    @NotYetImplemented
    def "intersection of two specs where one spec contains a superset of the rules of the other returns the spec containing the superset"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = excludeRule("org2", "*")
        def rule4 = excludeRule("*", "module3")
        def spec = excludeAny(rule1, rule2, rule3, rule4)
        def spec2 = excludeAny(rule2, rule1, rule4)

        expect:
        intersect(spec, spec2).is(spec)
        intersect(spec2, spec).is(spec)
    }

    def "intersection does not accept module that is not accepted by any merged exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeAny(rule1)

        expect:
        def intersection = intersect(spec, spec2)

        spec.excludeModule(moduleId("org", "module"))
        intersection.excludeModule(moduleId("org", "module"))

        spec.excludeModule(moduleId("org", "module2"))
        intersection.excludeModule(moduleId("org", "module2"))

        !spec.excludeModule(moduleId("org", "module3"))
        !spec2.excludeModule(moduleId("org", "module3"))
        !intersection.excludeModule(moduleId("org", "module3"))
    }

    def "intersection accepts artifact that is accepted by every merged exclude rule"() {
        def moduleId = moduleId("org", "module")
        def excludeA = excludeRule("org", "module", "a")
        def excludeB = excludeRule("org", "module", "b")
        def spec = excludeAny(excludeA, excludeB)
        def spec2 = excludeAny(excludeA)

        expect:
        def intersection = intersect(spec, spec2)

        intersection.excludeArtifact(moduleId, artifactName("a", "zip", "zip"))
        intersection.excludeArtifact(moduleId, artifactName("b", "zip", "zip"))
        !intersection.excludeArtifact(moduleId, artifactName("c", "zip", "zip"))

        intersection.mayExcludeArtifacts()
    }

    def "intersection of two specs with exclude rules is the union of the exclude rules"() {
        def rule1 = excludeRule("org", "module")
        def rule2 = excludeRule("org", "module2")
        def spec = excludeAny(rule1, rule2)
        def spec2 = excludeAny(rule1)

        expect:
        def intersection = intersect(spec, spec2)
        intersection == excludeAny(rule1, rule2)
    }

    def "intersections accepts same modules when original specs accept same modules"() {
        def rule1 = regexpExcludeRule("org", "module")
        def rule2 = regexpExcludeRule("org", "module2")
        def rule3 = regexpExcludeRule("org", "module3")
        def spec1 = union(excludeAny(rule1), excludeAny(rule2))
        def spec2 = union(excludeAny(rule2), excludeAny(rule1))
        def spec3 = excludeAny(rule3)
        assert spec1.excludesSameModulesAs(spec2)

        expect:
        intersect(spec1, spec2).excludesSameModulesAs(intersect(spec2, spec1))

        !intersect(spec1, spec2).excludesSameModulesAs(spec1)
        !intersect(spec1, spec2).excludesSameModulesAs(spec2)
        !intersect(spec1, spec2).excludesSameModulesAs(intersect(spec1, spec3))
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
        def spec = excludeAny(rule1, rule2, rule3, rule4, rule5, rule6, rule7, rule8)

        expect:
        spec.excludeArtifact(moduleId("org", "module"), artifactName("a", "jar", "jar"))
        spec.excludeArtifact(moduleId("org", "module2"), artifactName("b", "jar", "jar"))
        spec.excludeArtifact(moduleId("org2", "anything"), artifactName("c", "jar", "jar"))
        spec.excludeArtifact(moduleId("other", "module4"), artifactName("d", "jar", "jar"))
        spec.excludeArtifact(moduleId("some", "app"), artifactName("e", "sources", "jar"))
        spec.excludeArtifact(moduleId("foo", "bar"), artifactName("f", "sources", "jar"))
        spec.excludeArtifact(moduleId("well", "known"), artifactName("g", "jar", "war"))
        spec.excludeArtifact(moduleId("other", "sample"), artifactName("regexp-99", "jar", "jar"))
        !spec.excludeArtifact(moduleId("some", "app"), artifactName("e", "jar", "jar"))
        !spec.excludeArtifact(moduleId("some", "app"), artifactName("e", "javadoc", "jar"))
        !spec.excludeArtifact(moduleId("foo", "bar"), artifactName("f", "jar", "jar"))
        !spec.excludeArtifact(moduleId("well", "known"), artifactName("g", "jar", "jar"))
        !spec.excludeArtifact(moduleId("well", "known"), artifactName("g", "jar", "zip"))
        !spec.excludeArtifact(moduleId("other", "sample"), artifactName("regexp", "jar", "jar"))
    }

    def "can merge excludes with default and non-default ivy pattern matchers"() {
        def simpleExclude = excludeAny(excludeModuleRule("module-exclude"))
        def regexpExclude = excludeAny(regexpExcludeRule("regexp-match", "*"))
        def unmergedUnion = union(simpleExclude, regexpExclude)
        def intersection = intersect(unmergedUnion, simpleExclude)

        expect:
        union(intersection, simpleExclude)
    }

    static ModuleExclusion union(ModuleExclusion spec, ModuleExclusion otherRule) {
        ModuleExclusions.union(spec, otherRule)
    }

    static ModuleExclusion intersect(ModuleExclusion spec, ModuleExclusion otherRule) {
        ModuleExclusions.intersect(spec, otherRule)
    }

    static specForRule(def spec, Exclude rule) {
        return spec.moduleId.group == rule.moduleId.group && spec.moduleId.name == rule.moduleId.name
    }

    def moduleId(String group, String name) {
        return DefaultModuleIdentifier.newId(group, name);
    }

    def artifactName(String name, String type, String ext) {
        return DefaultIvyArtifactName.of(name, type, ext)
    }

    def excludeRule(String org, String module, String name = "*", String type = "*", String ext = "*") {
        new DefaultExclude(org, module, name, type, ext, new String[0], PatternMatchers.EXACT)
    }

    def excludeModuleRule(String module) {
        new DefaultExclude("*", module, "*", "*", "*", new String[0], PatternMatchers.EXACT)
    }

    def excludeGroupRule(String group) {
        new DefaultExclude(group, "*", "*", "*", "*", new String[0], PatternMatchers.EXACT)
    }

    def excludeArtifactRule(String name, String type, String ext) {
        excludeRule("*", "*", name, type, ext)
    }

    def regexpExcludeRule(String org, String module, String name = "*", String type = "*", String ext = "*") {
        new DefaultExclude(org, module, name, type, ext, new String[0], "regexp")
    }

    def regexpExcludeArtifactRule(String name, String type, String ext) {
        regexpExcludeRule("*", "*", name, type, ext)
    }
}
