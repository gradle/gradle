/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories


import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification
import spock.lang.Subject

class DefaultRepositoryContentDescriptorTest extends Specification {

    @Subject
    DefaultRepositoryContentDescriptor descriptor = new DefaultMavenRepositoryContentDescriptor({ throw new RuntimeException("only required in error cases") }, new VersionParser())

    def "reasonable error message when input is incorrect (include string)"() {
        when:
        descriptor.includeGroup(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Group cannot be null"

        when:
        descriptor.includeGroupAndSubgroups(null)

        then:
        ex = thrown()
        ex.message == "Group prefix cannot be null"

        when:
        descriptor.includeModule("foo", null)

        then:
        ex = thrown()
        ex.message == "Module name cannot be null"

        when:
        descriptor.includeModule(null, "foo")

        then:
        ex = thrown()
        ex.message == "Group cannot be null"

        when:
        descriptor.includeVersion("foo", "bar", null)

        then:
        ex = thrown()
        ex.message == "Version cannot be null"

        when:
        descriptor.includeVersion("foo", null, "1.0")

        then:
        ex = thrown()
        ex.message == "Module name cannot be null"

        when:
        descriptor.includeVersion(null, "foo", "1.0")

        then:
        ex = thrown()
        ex.message == "Group cannot be null"
    }

    def "reasonable error message when input is incorrect (include regex)"() {
        when:
        descriptor.includeGroupByRegex(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Group regex cannot be null"

        when:
        descriptor.includeModuleByRegex("foo", null)

        then:
        ex = thrown()
        ex.message == "Module name regex cannot be null"

        when:
        descriptor.includeModuleByRegex(null, "foo")

        then:
        ex = thrown()
        ex.message == "Group regex cannot be null"

        when:
        descriptor.includeVersionByRegex("foo", "bar", null)

        then:
        ex = thrown()
        ex.message == "Version regex cannot be null"

        when:
        descriptor.includeVersionByRegex("foo", null, "1.0")

        then:
        ex = thrown()
        ex.message == "Module name regex cannot be null"

        when:
        descriptor.includeVersionByRegex(null, "foo", "1.0")

        then:
        ex = thrown()
        ex.message == "Group regex cannot be null"
    }

    def "reasonable error message when input is incorrect (exclude string)"() {
        when:
        descriptor.excludeGroup(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Group cannot be null"

        when:
        descriptor.excludeGroupAndSubgroups(null)

        then:
        ex = thrown()
        ex.message == "Group prefix cannot be null"

        when:
        descriptor.excludeModule("foo", null)

        then:
        ex = thrown()
        ex.message == "Module name cannot be null"

        when:
        descriptor.excludeModule(null, "foo")

        then:
        ex = thrown()
        ex.message == "Group cannot be null"

        when:
        descriptor.excludeVersion("foo", "bar", null)

        then:
        ex = thrown()
        ex.message == "Version cannot be null"

        when:
        descriptor.excludeVersion("foo", null, "1.0")

        then:
        ex = thrown()
        ex.message == "Module name cannot be null"

        when:
        descriptor.excludeVersion(null, "foo", "1.0")

        then:
        ex = thrown()
        ex.message == "Group cannot be null"
    }

    def "reasonable error message when input is incorrect (exclude regex)"() {
        when:
        descriptor.excludeGroupByRegex(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Group regex cannot be null"

        when:
        descriptor.excludeModuleByRegex("foo", null)

        then:
        ex = thrown()
        ex.message == "Module name regex cannot be null"

        when:
        descriptor.excludeModuleByRegex(null, "foo")

        then:
        ex = thrown()
        ex.message == "Group regex cannot be null"

        when:
        descriptor.excludeVersionByRegex("foo", "bar", null)

        then:
        ex = thrown()
        ex.message == "Version regex cannot be null"

        when:
        descriptor.excludeVersionByRegex("foo", null, "1.0")

        then:
        ex = thrown()
        ex.message == "Module name regex cannot be null"

        when:
        descriptor.excludeVersionByRegex(null, "foo", "1.0")

        then:
        ex = thrown()
        ex.message == "Group regex cannot be null"
    }

    def "can exclude or include whole groups using #method(#expr)"() {
        def fooMod = DefaultModuleIdentifier.newId(group, module)
        def details = Mock(ArtifactResolutionDetails)
        def descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())

        given:
        descriptor."exclude$method"(expr)

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            1 * details.notFound()
        } else {
            0 * details.notFound()
        }


        when:
        descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())
        descriptor."include$method"(expr)
        action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            0 * details.notFound()
        } else {
            1 * details.notFound()
        }

        where:
        method              | expr     | group           | module | version | excluded
        'Group'             | 'org'    | 'org'           | 'foo'  | '1.0'   | true
        'Group'             | 'org'    | 'other'         | 'foo'  | '1.0'   | false
        'GroupByRegex'      | 'org'    | 'org'           | 'foo'  | '1.0'   | true
        'GroupByRegex'      | 'org'    | 'other'         | 'foo'  | '1.0'   | false
        'GroupByRegex'      | 'bar'    | 'org'           | 'foo'  | '1.0'   | false
        'GroupByRegex'      | '[org]+' | 'org'           | 'foo'  | '1.0'   | true
        'GroupByRegex'      | '[org]+' | 'other'         | 'foo'  | '1.0'   | false
        'GroupAndSubgroups' | 'org'    | 'org'           | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | 'org'    | 'org.other'     | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | 'org'    | 'org1.other'    | 'foo'  | '1.0'   | false
        'GroupAndSubgroups' | 'org'    | 'ORG.other'     | 'foo'  | '1.0'   | false
        'GroupAndSubgroups' | 'org'    | 'org.other.org' | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | 'org.'   | 'org.other'     | 'foo'  | '1.0'   | false
        'GroupAndSubgroups' | 'org.'   | 'org..other'    | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | 'org'    | 'org.org.other' | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | '.'      | 'org.other'     | 'foo'  | '1.0'   | false
        'GroupAndSubgroups' | '.'      | '..other'       | 'foo'  | '1.0'   | true
        'GroupAndSubgroups' | 'nope'   | 'org.other'     | 'foo'  | '1.0'   | false
    }

    def "can exclude or include whole modules using #method(#expr)"() {
        def fooMod = DefaultModuleIdentifier.newId(group, module)
        def details = Mock(ArtifactResolutionDetails)
        def descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())

        given:
        descriptor."exclude$method"(group, expr)

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            1 * details.notFound()
        } else {
            0 * details.notFound()
        }


        when:
        descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())
        descriptor."include$method"(group, expr)
        action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            0 * details.notFound()
        } else {
            1 * details.notFound()
        }

        where:
        method          | expr    | group | module | version | excluded
        'Module'        | 'foo'   | 'org' | 'foo'  | '1.0'   | true
        'Module'        | 'foo'   | 'org' | 'bar'  | '1.0'   | false
        'ModuleByRegex' | 'foo'   | 'org' | 'foo'  | '1.0'   | true
        'ModuleByRegex' | 'foo'   | 'org' | 'bar'  | '1.0'   | false
        'ModuleByRegex' | 'bar'   | 'org' | 'foo'  | '1.0'   | false
        'ModuleByRegex' | 'f[o]+' | 'org' | 'foo'  | '1.0'   | true
        'ModuleByRegex' | 'f[o]+' | 'org' | 'bar'  | '1.0'   | false
    }

    def "can exclude or include specific versions using #method(#expr)"() {
        def fooMod = DefaultModuleIdentifier.newId(group, module)
        def details = Mock(ArtifactResolutionDetails)
        def descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())

        given:
        descriptor."exclude$method"(group, module, expr)

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            1 * details.notFound()
        } else {
            0 * details.notFound()
        }


        when:
        descriptor = new DefaultRepositoryContentDescriptor({ "my-repo" }, new VersionParser())
        descriptor."include$method"(group, module, expr)
        action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, version)
        action.execute(details)

        then:
        if (excluded) {
            0 * details.notFound()
        } else {
            1 * details.notFound()
        }

        where:
        method           | expr        | group | module | version | excluded
        'Version'        | '1.0'       | 'org' | 'foo'  | '1.0'   | true
        'Version'        | '1.1'       | 'org' | 'bar'  | '1.0'   | false
        'Version'        | '[1.0,)'    | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '[1.1,)'    | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '[1.0,1.2]' | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '[1.0,1.1]' | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '[1.0,1.2)' | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '[1.0,1.2]' | 'org' | 'bar'  | '1.3'   | false
        'Version'        | '(,1.2]'    | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '(,1.1]'    | 'org' | 'bar'  | '1.1'   | true
        'Version'        | '(,1.0]'    | 'org' | 'bar'  | '1.1'   | false
        'VersionByRegex' | '1\\.0'     | 'org' | 'foo'  | '1.0'   | true
        'VersionByRegex' | '1\\.1'     | 'org' | 'bar'  | '1.0'   | false
        'VersionByRegex' | '2.+'       | 'org' | 'foo'  | '1.0'   | false
        'VersionByRegex' | '1.+'       | 'org' | 'foo'  | '1.0'   | true
    }

    def "cannot update repository content filter after resolution happens"() {
        given:
        def descriptor = new DefaultRepositoryContentDescriptor({ "repoName" }, new VersionParser())
        descriptor.toContentFilter()

        when:
        descriptor.excludeGroup("foo")

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Cannot mutate content repository descriptor 'repoName' after repository has been used"
    }

    def "can include and exclude at the same time"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def barMod = DefaultModuleIdentifier.newId('org', 'bar')
        def bazMod = DefaultModuleIdentifier.newId('org2', 'baz')
        def details = Mock(ArtifactResolutionDetails)

        given:
        descriptor.includeGroup("org")
        descriptor.excludeModule("org", "bar")

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        action.execute(details)

        then:
        0 * details.notFound()

        when:
        details.moduleId >> barMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(barMod, '1.0')
        action.execute(details)

        then:
        1 * details.notFound()

        when:
        details.moduleId >> bazMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(bazMod, '1.0')
        action.execute(details)

        then:
        1 * details.notFound()
    }

    def "excluding resets the inclusions"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def details = Mock(ArtifactResolutionDetails)

        given:
        descriptor.includeGroup("org")

        when:
        descriptor.excludeGroup("org")
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        action.execute(details)

        then:
        1 * details.notFound()
    }

    def "excludes are cumulative"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def details = Mock(ArtifactResolutionDetails)

        given:
        descriptor.excludeGroup("g1")
        descriptor.excludeGroup("org")
        descriptor.excludeGroup("g2")

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        action.execute(details)

        then:
        1 * details.notFound()
    }

    def "includes are cumulative"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def details = Mock(ArtifactResolutionDetails)

        given:
        descriptor.includeGroup("g1")
        descriptor.includeGroup("org")
        descriptor.includeGroup("g2")

        when:
        def action = descriptor.toContentFilter()
        details.moduleId >> fooMod
        details.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        action.execute(details)

        then:
        0 * details.notFound()
    }

    def "the same rule declared on independent descriptors is a single spec instance"() {
        given:
        def first = new DefaultRepositoryContentDescriptor({ "first" }, new VersionParser())
        def second = new DefaultRepositoryContentDescriptor({ "second" }, new VersionParser())

        when:
        first.includeGroup("org")
        second.includeGroup("org")
        second.includeGroup("other")

        then:
        first.includeSpecs.size() == 1
        second.includeSpecs.size() == 2
        second.includeSpecs.any { it.is(first.includeSpecs.first()) }
        !second.includeSpecs.every { it.is(first.includeSpecs.first()) }
    }

    def "an include and an exclude of the same coordinates are not the same spec"() {
        given:
        def including = new DefaultRepositoryContentDescriptor({ "including" }, new VersionParser())
        def excluding = new DefaultRepositoryContentDescriptor({ "excluding" }, new VersionParser())

        when:
        including.includeGroup("org")
        excluding.excludeGroup("org")

        then:
        !excluding.excludeSpecs.first().is(including.includeSpecs.first())
    }

    def "descriptors sharing a spec filter independently, including dynamic versions"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def firstDetails = Mock(ArtifactResolutionDetails)
        def secondDetails = Mock(ArtifactResolutionDetails)

        given:
        def first = new DefaultRepositoryContentDescriptor({ "first" }, new VersionParser())
        def second = new DefaultRepositoryContentDescriptor({ "second" }, new VersionParser())
        first.excludeVersion("org", "foo", "[1.0,1.2]")
        second.excludeVersion("org", "foo", "[1.0,1.2]")

        expect: "the rule is declared once and both descriptors see the same spec"
        first.excludeSpecs.first().is(second.excludeSpecs.first())

        when: "each descriptor resolves the shared spec through its own selector scheme"
        def firstAction = first.toContentFilter()
        def secondAction = second.toContentFilter()
        firstDetails.moduleId >> fooMod
        firstDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.1')
        secondDetails.moduleId >> fooMod
        secondDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.1')
        firstAction.execute(firstDetails)
        secondAction.execute(secondDetails)

        then: "a version inside the range is excluded by both"
        1 * firstDetails.notFound()
        1 * secondDetails.notFound()

        when:
        firstDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.3')
        secondDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.3')
        firstAction.execute(firstDetails)
        secondAction.execute(secondDetails)

        then: "a version outside it is excluded by neither"
        0 * firstDetails.notFound()
        0 * secondDetails.notFound()
    }

    def "a mutable copy of a descriptor holding shared specs can take further rules"() {
        def fooMod = DefaultModuleIdentifier.newId('org', 'foo')
        def barMod = DefaultModuleIdentifier.newId('org', 'bar')
        def originalDetails = Mock(ArtifactResolutionDetails)
        def copyDetails = Mock(ArtifactResolutionDetails)

        given: "another descriptor already declared the rule, so the spec is shared"
        new DefaultRepositoryContentDescriptor({ "other" }, new VersionParser()).excludeModule("org", "foo")
        descriptor.excludeModule("org", "foo")

        when:
        def copy = descriptor.asMutableCopy()
        copy.excludeModule("org", "bar")
        def originalAction = descriptor.toContentFilter()
        def copyAction = copy.toContentFilter()
        originalDetails.moduleId >> barMod
        originalDetails.componentId >> DefaultModuleComponentIdentifier.newId(barMod, '1.0')
        copyDetails.moduleId >> barMod
        copyDetails.componentId >> DefaultModuleComponentIdentifier.newId(barMod, '1.0')
        originalAction.execute(originalDetails)
        copyAction.execute(copyDetails)

        then: "the rule added to the copy does not reach the original"
        0 * originalDetails.notFound()
        1 * copyDetails.notFound()

        when:
        originalDetails.moduleId >> fooMod
        originalDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        copyDetails.moduleId >> fooMod
        copyDetails.componentId >> DefaultModuleComponentIdentifier.newId(fooMod, '1.0')
        originalAction.execute(originalDetails)
        copyAction.execute(copyDetails)

        then: "and the shared rule still applies to both"
        1 * originalDetails.notFound()
        1 * copyDetails.notFound()
    }

}
