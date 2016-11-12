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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        ProjectComponentIdentifier defaultBuildComponentIdentifier = newProjectId(':myPath')

        then:
        defaultBuildComponentIdentifier.projectPath == ':myPath'
        defaultBuildComponentIdentifier.displayName == 'project :myPath'
        defaultBuildComponentIdentifier.toString() == 'project :myPath'
    }

    def "non-current build includes build name in display name"() {
        when:
        ProjectComponentIdentifier defaultBuildComponentIdentifier = newProjectId(":myPath", false)

        then:
        defaultBuildComponentIdentifier.projectPath == ':myPath'
        defaultBuildComponentIdentifier.displayName == 'project :TEST:myPath'
        defaultBuildComponentIdentifier.toString() == 'project :TEST:myPath'
    }

    def "is instantiated with null constructor parameter value"() {
        when:
        newProjectId((String) null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    @Unroll
    def "can compare with other instance (#projectPath)"() {
        expect:
        ProjectComponentIdentifier defaultBuildComponentIdentifier1 = newProjectId(':myProjectPath1')
        ProjectComponentIdentifier defaultBuildComponentIdentifier2 = newProjectId(projectPath)
        strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2) == equality
        (defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode()) == hashCode
        (defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString()) == stringRepresentation

        where:
        projectPath       | equality | hashCode | stringRepresentation
        ':myProjectPath1' | true     | true     | true
        ':myProjectPath2' | false    | false    | false
    }

    private static newProjectId(String path, boolean current = true) {
        new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier("TEST", current), path)
    }

}
