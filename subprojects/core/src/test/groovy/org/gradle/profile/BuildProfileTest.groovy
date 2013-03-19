/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.profile

import org.gradle.StartParameter
import org.gradle.api.Project
import spock.lang.Specification

class BuildProfileTest extends Specification {
    final BuildProfile profile = new BuildProfile()

    def "creates dependency set profile on first get"() {
        expect:
        def dependencyProfile = profile.getDependencySetProfile("path")
        dependencyProfile != null
        profile.getDependencySetProfile("path") == dependencyProfile
    }

    def "can get all dependency set profiles"() {
        given:
        def a = profile.getDependencySetProfile("a")
        def b = profile.getDependencySetProfile("b")

        expect:
        profile.dependencySets.operations == [a, b]
    }

    def "can get all project configuration profiles"() {
        given:
        def a = profile.getProjectProfile(project("a"))
        def b = profile.getProjectProfile(project("b"))

        expect:
        profile.projectConfiguration.operations == [a.evaluation, b.evaluation]
    }

    def "contains build description"() {
        given:
        def param = new StartParameter()
        param.setTaskNames(["foo", "bar"])
        param.setExcludedTaskNames(["one", "two"])

        when:
        profile.setBuildDescription(param)

        then:
        profile.buildDescription.contains(" -x one -x two foo bar")
    }

    def "build description looks nice even if no tasks specified"() {
        given:
        def param = new StartParameter()

        when:
        profile.setBuildDescription(param)

        then:
        profile.buildDescription.contains(" (no tasks specified)")
    }

    def "provides start time description"() {
        when:
        profile.buildStarted = new GregorianCalendar(2010, 1, 1, 12, 25).getTimeInMillis()

        then:
        profile.buildStartedDescription == "Started on: 2010/02/01 - 12:25:00"
    }

    def project(String path) {
        Project project = Mock()
        _ * project.path >> path
        return project
    }
}
