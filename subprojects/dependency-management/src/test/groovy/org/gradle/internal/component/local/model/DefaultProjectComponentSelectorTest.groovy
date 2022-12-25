/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newSelector
import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectComponentSelectorTest extends Specification {

    def "is instantiated with non-null constructor parameter values"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = new DefaultProjectComponentSelector(Stub(BuildIdentifier), Path.path(":id:path"), Path.path(":project:path"), "projectName", ImmutableAttributes.EMPTY, [])

        then:
        defaultBuildComponentSelector.projectPath == ":project:path"
        defaultBuildComponentSelector.projectName == "projectName"
        defaultBuildComponentSelector.displayName == "project :id:path"
        defaultBuildComponentSelector.toString() == "project :id:path"
    }

    def "can compare with other instance (#projectPath)"() {
        expect:
        ProjectComponentSelector defaultBuildComponentSelector1 = newSelector(':myProjectPath1')
        ProjectComponentSelector defaultBuildComponentSelector2 = newSelector(projectPath)
        strictlyEquals(defaultBuildComponentSelector1, defaultBuildComponentSelector2) == equality
        (defaultBuildComponentSelector1.hashCode() == defaultBuildComponentSelector2.hashCode()) == hashCode
        (defaultBuildComponentSelector1.toString() == defaultBuildComponentSelector2.toString()) == stringRepresentation

        where:
        projectPath       | equality | hashCode | stringRepresentation
        ':myProjectPath1' | true     | true     | true
        ':myProjectPath2' | false    | false    | false
    }

    def "prevents matching of null id"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = newSelector(':myPath')
        defaultBuildComponentSelector.matchesStrictly(null)

        then:
        Throwable t = thrown(AssertionError)
        assert t.message == 'identifier cannot be null'
    }

    def "does not match id for unexpected component selector type"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = newSelector(':myPath')
        boolean matches = defaultBuildComponentSelector.matchesStrictly(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', 'name'), '1.0'))

        then:
        assert !matches
    }

    def "matches id (#buildName #projectPath)"() {
        expect:
        def selector = new DefaultProjectComponentSelector(Stub(BuildIdentifier), Path.path(":id:path"), Path.path(":project:path"), "projectName", ImmutableAttributes.EMPTY, [])
        def sameIdPath = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path(":id:path"), Path.path(":project:path"), "projectName")
        def differentIdPath = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path(":id:path2"), Path.path(":project:path"), "projectName")
        selector.matchesStrictly(sameIdPath)
        !selector.matchesStrictly(differentIdPath)
    }
}
