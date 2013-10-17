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

import org.gradle.api.Project
import org.gradle.api.artifacts.component.BuildComponentIdentifier
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultBuildComponentIdentifierTest extends Specification {
    Project project1 = Mock()
    Project project2 = Mock()

    def "is instantiated with non-null constructor parameter values"() {
        when:
        BuildComponentIdentifier defaultBuildComponentIdentifier = new DefaultBuildComponentIdentifier(project1)

        then:
        defaultBuildComponentIdentifier.project == project1
        1 * project1.path >> ':myPath'
        defaultBuildComponentIdentifier.displayName == 'project :myPath'
        defaultBuildComponentIdentifier.toString() == 'project :myPath'
    }

    @Unroll
    def "is instantiated with null constructor parameter value"() {
        when:
        new DefaultBuildComponentIdentifier(null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project cannot be null'
    }

    def "can compare with instance for same project"() {
        when:
        BuildComponentIdentifier defaultBuildComponentIdentifier1 = new DefaultBuildComponentIdentifier(project1)
        BuildComponentIdentifier defaultBuildComponentIdentifier2 = new DefaultBuildComponentIdentifier(project1)

        then:
        strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2)
        (defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode())
        (defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString())
        2 * project1.path >> ':myProjectPath1'
    }

    def "can compare with instance for different projects"() {
        when:
        BuildComponentIdentifier defaultBuildComponentIdentifier1 = new DefaultBuildComponentIdentifier(project1)
        BuildComponentIdentifier defaultBuildComponentIdentifier2 = new DefaultBuildComponentIdentifier(project2)

        then:
        !strictlyEquals(defaultBuildComponentIdentifier1, defaultBuildComponentIdentifier2)
        !(defaultBuildComponentIdentifier1.hashCode() == defaultBuildComponentIdentifier2.hashCode())
        !(defaultBuildComponentIdentifier1.toString() == defaultBuildComponentIdentifier2.toString())
        1 * project1.path >> ':myProjectPath1'
        1 * project2.path >> ':myProjectPath2'
    }
}
