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

import spock.lang.Specification
import org.gradle.api.invocation.Gradle
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.Project
import org.gradle.StartParameter

class BuildProfileTest extends Specification {
    final BuildProfile profile = new BuildProfile()

    def "creates dependency set profile on first get"() {
        given:
        ResolvableDependencies deps = dependencySet("path")

        expect:
        def dependencyProfile = profile.getDependencySetProfile(deps)
        dependencyProfile != null
        profile.getDependencySetProfile(deps) == dependencyProfile
    }

    def "can get all dependency set profiles"() {
        given:
        def a = profile.getDependencySetProfile(dependencySet("a"))
        def b = profile.getDependencySetProfile(dependencySet("b"))

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

    def dependencySet(String path) {
        ResolvableDependencies dependencies = Mock()
        _ * dependencies.path >> path
        return dependencies
    }

    def project(String path) {
        Project project = Mock()
        _ * project.path >> path
        return project
    }
}
