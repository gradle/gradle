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
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId
import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newSelector
import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectComponentSelectorTest extends Specification {

    def "is instantiated with non-null constructor parameter values"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = newSelector(buildName , projectPath)

        then:
        defaultBuildComponentSelector.projectPath == projectPath
        defaultBuildComponentSelector.displayName == displayName
        defaultBuildComponentSelector.toString() == displayName

        where:
        buildName | projectPath | displayName
        ':'       | ':myPath'   | 'project :myPath'
        'build'   | ':myPath'   | 'project :build:myPath'
    }

    def "is instantiated with null constructor parameter value"() {
        when:
        new DefaultProjectComponentSelector(Stub(BuildIdentifier), (String) null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    @Unroll
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
        boolean matches = defaultBuildComponentSelector.matchesStrictly(new DefaultModuleComponentIdentifier('group', 'name', '1.0'))

        then:
        assert !matches
    }

    @Unroll
    def "matches id (#buildName #projectPath)"() {
        expect:
        ProjectComponentSelector defaultBuildComponentSelector = newSelector(buildName, ':myProjectPath1')
        ProjectComponentIdentifier defaultBuildComponentIdentifier = newProjectId("TEST", projectPath)
        defaultBuildComponentSelector.matchesStrictly(defaultBuildComponentIdentifier) == matchesId

        where:
        buildName | projectPath       | matchesId
        'TEST'    | ':myProjectPath1' | true
        'TEST'    | ':myProjectPath2' | false
        'OTHER'   | ':myProjectPath1' | false
        ':'       | ':myProjectPath1' | false
    }
}
