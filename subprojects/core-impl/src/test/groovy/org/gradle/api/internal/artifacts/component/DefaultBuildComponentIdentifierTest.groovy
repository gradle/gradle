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

import org.gradle.api.artifacts.component.BuildComponentIdentifier
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultBuildComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        BuildComponentIdentifier defaultBuildComponentIdentifier = new DefaultBuildComponentIdentifier(':myPath')

        then:
        defaultBuildComponentIdentifier.projectPath == ':myPath'
        defaultBuildComponentIdentifier.displayName == 'project :myPath'
        defaultBuildComponentIdentifier.toString() == 'project :myPath'
    }

    def "is instantiated with null constructor parameter value"() {
        when:
        new DefaultBuildComponentIdentifier(null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project path cannot be null'
    }

    @Unroll
    def "can compare with other instance (#projectPath)"() {
        expect:
        BuildComponentIdentifier defaultBuildComponentIdentifier1 = new DefaultBuildComponentIdentifier(':myProjectPath1')
        BuildComponentIdentifier defaultBuildComponentIdentifier2 = new DefaultBuildComponentIdentifier(projectPath)
        strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2) == equality
        (defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode()) == hashCode
        (defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString()) == stringRepresentation

        where:
        projectPath       | equality | hashCode | stringRepresentation
        ':myProjectPath1' | true     | true     | true
        ':myProjectPath2' | false    | false    | false
    }
}
