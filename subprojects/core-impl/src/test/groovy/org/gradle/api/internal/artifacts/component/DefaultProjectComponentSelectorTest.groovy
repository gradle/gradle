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
package org.gradle.api.internal.artifacts.component

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectComponentSelectorTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = new DefaultProjectComponentSelector(':myPath')

        then:
        defaultBuildComponentSelector.projectPath == ':myPath'
        defaultBuildComponentSelector.displayName == 'project :myPath'
        defaultBuildComponentSelector.toString() == 'project :myPath'
    }

    def "is instantiated with null constructor parameter value"() {
        when:
        new DefaultProjectComponentSelector(null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    @Unroll
    def "can compare with other instance (#projectPath)"() {
        expect:
        ProjectComponentSelector defaultBuildComponentSelector1 = new DefaultProjectComponentSelector(':myProjectPath1')
        ProjectComponentSelector defaultBuildComponentSelector2 = new DefaultProjectComponentSelector(projectPath)
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
        ProjectComponentSelector defaultBuildComponentSelector = new DefaultProjectComponentSelector(':myPath')
        defaultBuildComponentSelector.matchesStrictly(null)

        then:
        Throwable t = thrown(AssertionError)
        assert t.message == 'identifier cannot be null'
    }

    def "does not match id for unexpected component selector type"() {
        when:
        ProjectComponentSelector defaultBuildComponentSelector = new DefaultProjectComponentSelector(':myPath')
        boolean matches = defaultBuildComponentSelector.matchesStrictly(new DefaultModuleComponentIdentifier('group', 'name', '1.0'))

        then:
        assert !matches
    }

    @Unroll
    def "matches id (#projectPath)"() {
        expect:
        ProjectComponentSelector defaultBuildComponentSelector = new DefaultProjectComponentSelector(':myProjectPath1')
        ProjectComponentIdentifier defaultBuildComponentIdentifier = new DefaultProjectComponentIdentifier(projectPath)
        defaultBuildComponentSelector.matchesStrictly(defaultBuildComponentIdentifier) == matchesId

        where:
        projectPath       | matchesId
        ':myProjectPath1' | true
        ':myProjectPath2' | false
    }
}
