/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers.multiParser
import static org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers.parser

class ComponentSelectorParsersTest extends Specification {

    def "understands group:name:version notation"() {
        when:
        def v = multiParser().parseNotation("org.foo:bar:1.0") as List

        then:
        v.size() == 1
        v[0] instanceof ModuleComponentSelector
        v[0].group == 'org.foo'
        v[0].module == 'bar'
        v[0].version == '1.0'
        v[0].versionConstraint.requiredVersion == '1.0'
        v[0].versionConstraint.preferredVersion == ''
        v[0].versionConstraint.strictVersion == ''
        v[0].versionConstraint.rejectedVersions == []
    }

    def "works with CharSequences"() {
        when:
        def sb = new StringBuilder().append("org.foo:charsequence:1.0")
        def v = multiParser().parseNotation(sb) as List

        then:
        v.size() == 1
        v[0] instanceof ModuleComponentSelector
        v[0].module == 'charsequence'
    }

    def "allows exact type on input"() {
        def module = DefaultModuleIdentifier.newId("org.foo", "bar")
        def id = DefaultModuleComponentSelector.newSelector(module, new DefaultMutableVersionConstraint("2.0"))

        when:
        def v = multiParser().parseNotation(id) as List

        then:
        v.size() == 1
        v[0] instanceof ModuleComponentSelector
        v[0].group == 'org.foo'
        v[0].module == 'bar'
        v[0].version == '2.0'
        v[0].versionConstraint.requiredVersion == '2.0'
        v[0].versionConstraint.preferredVersion == ''
        v[0].versionConstraint.strictVersion == ''
        v[0].versionConstraint.rejectedVersions == []
    }

    def "allows list of objects on input"() {
        def module = DefaultModuleIdentifier.newId("org.foo", "bar")
        def id = DefaultModuleComponentSelector.newSelector(module, new DefaultMutableVersionConstraint("2.0"))

        when:
        def v = multiParser().parseNotation([id, ["hey:man:1.0"], [group: 'i', name: 'like', version: 'maps']]) as List

        then:
        v.size() == 3
        v[0].module == 'bar'
        v[1].module == 'man'
        v[2].module == 'like'
    }

    def "allows map on input"() {
        when:
        def v = multiParser().parseNotation([group: 'org.foo', name: 'bar', version: '1.0']) as List

        then:
        v.size() == 1
        v[0] instanceof ModuleComponentSelector
        v[0].group == 'org.foo'
        v[0].module == 'bar'
        v[0].version == '1.0'
        v[0].versionConstraint.requiredVersion == '1.0'
        v[0].versionConstraint.preferredVersion == ''
        v[0].versionConstraint.strictVersion == ''
        v[0].versionConstraint.rejectedVersions == []
    }

    def "understands project input"() {
        when:
        def buildId = new DefaultBuildIdentifier(Path.path(":build"))
        def projectState = Mock(ProjectState) {
            getIdentity() >> new ProjectIdentity(buildId, Path.path(":id:bar"), Path.path(":bar"), "name")
        }
        def project = Mock(ProjectInternal) {
            getIdentityPath() >> Path.path(":id:bar")
            getProjectPath() >> Path.path(":bar")
            getName() >> "name"
            getOwner() >> projectState
        }
        def v = multiParser().parseNotation(project) as List

        then:
        v.size() == 1
        v[0] instanceof ProjectComponentSelector
        v[0].projectPath == ":bar"
        v[0].buildName == "build"
    }

    def "fails for unknown types"() {
        when:
        multiParser().parseNotation(new Object())

        then:
        thrown(UnsupportedNotationException)
    }

    def "reports missing keys for map notation"() {
        when:
        multiParser().parseNotation([name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports wrong keys for map notation"() {
        when:
        multiParser().parseNotation([groop: 'groop', name: "bar", version: "1.0"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid format for string notation"() {
        when:
        multiParser().parseNotation(["blahblah"])

        then:
        thrown(InvalidUserDataException)
    }

    def "reports invalid missing data for string notation"() {
        when:
        multiParser().parseNotation([":foo:"])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message.contains 'cannot be empty'
    }

    def "null is an invalid input"() {
        when:
        multiParser().parseNotation(null)

        then:
        thrown(UnsupportedNotationException)

        when:
        parser().parseNotation(null)

        then:
        thrown(UnsupportedNotationException)
    }

    def "single parser understands String notation"() {
        //just smoke testing the single parser, it is covered in multiParser, too.
        when:
        def v = parser().parseNotation("org.foo:bar:1.0")

        then:
        v instanceof ModuleComponentSelector
        v.group == 'org.foo'
        v.module == 'bar'
        v.version == '1.0'
        v.versionConstraint.requiredVersion == '1.0'
        v.versionConstraint.preferredVersion == ''
        v.versionConstraint.strictVersion == ''
        v.versionConstraint.rejectedVersions == []
    }
}
