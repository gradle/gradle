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
import org.gradle.api.artifacts.component.BuildComponentSelector
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.Matchers.strictlyEquals

class DefaultBuildComponentSelectorTest extends Specification {
    Project project1 = Mock()
    Project project2 = Mock()

    def "is instantiated with non-null constructor parameter values"() {
        when:
        BuildComponentSelector defaultBuildComponentSelector = new DefaultBuildComponentSelector(project1)

        then:
        defaultBuildComponentSelector.project == project1
        1 * project1.path >> ':myPath'
        defaultBuildComponentSelector.displayName == 'project :myPath'
        defaultBuildComponentSelector.toString() == 'project :myPath'
    }

    @Unroll
    def "is instantiated with null constructor parameter value"() {
        when:
        new DefaultBuildComponentSelector(null)

        then:
        Throwable t = thrown(AssertionError)
        t.message == 'project cannot be null'
    }

    def "can compare with instance for same project"() {
        when:
        BuildComponentSelector defaultBuildComponentSelector1 = new DefaultBuildComponentSelector(project1)
        BuildComponentSelector defaultBuildComponentSelector2 = new DefaultBuildComponentSelector(project1)

        then:
        strictlyEquals(defaultBuildComponentSelector1, defaultBuildComponentSelector2)
        (defaultBuildComponentSelector1.hashCode() == defaultBuildComponentSelector2.hashCode())
        (defaultBuildComponentSelector1.toString() == defaultBuildComponentSelector2.toString())
        2 * project1.path >> ':myProjectPath1'
    }

    def "can compare with instance for different projects"() {
        when:
        BuildComponentSelector defaultBuildComponentSelector1 = new DefaultBuildComponentSelector(project1)
        BuildComponentSelector defaultBuildComponentSelector2 = new DefaultBuildComponentSelector(project2)

        then:
        !strictlyEquals(defaultBuildComponentSelector1, defaultBuildComponentSelector2)
        !(defaultBuildComponentSelector1.hashCode() == defaultBuildComponentSelector2.hashCode())
        !(defaultBuildComponentSelector1.toString() == defaultBuildComponentSelector2.toString())
        1 * project1.path >> ':myProjectPath1'
        1 * project2.path >> ':myProjectPath2'
    }
}
