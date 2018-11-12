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
import org.gradle.api.tasks.TaskState
import spock.lang.Specification

class BuildProfileTest extends Specification {
    private profile = new BuildProfile(new StartParameter())

    def "creates dependency set profile on first get"() {
        expect:
        def dependencyProfile = profile.getDependencySetProfile("path")
        dependencyProfile != null
        profile.getDependencySetProfile("path") == dependencyProfile
    }

    def "provides sorted dependency set profiles"() {
        given:
        def a = profile.getDependencySetProfile("a").setStart(100).setFinish(200)
        def b = profile.getDependencySetProfile("b").setStart(200).setFinish(400)
        def c = profile.getDependencySetProfile("c").setStart(400).setFinish(600)
        def d = profile.getDependencySetProfile("d").setStart(600).setFinish(601)

        expect:
        profile.dependencySets.operations == [b, c, a, d]
    }

    def "provides sorted configuration profiles"() {
        given:
        def a = profile.getProjectProfile("a").configurationOperation.setStart(100).setFinish(200)
        def b = profile.getProjectProfile("b").configurationOperation.setStart(200).setFinish(500)
        def c = profile.getProjectProfile("c").configurationOperation.setStart(500).setFinish(800)
        def d = profile.getProjectProfile("d").configurationOperation.setStart(800).setFinish(850)

        expect:
        profile.projectConfiguration.operations == [b, c, a, d]
    }

    def "provides sorted project profiles"() {
        given:
        profile.getProjectProfile("a").getTaskProfile("a:x").completed(Stub(TaskState)).setStart(100).setFinish(300)
        profile.getProjectProfile("b").getTaskProfile("b:x").completed(Stub(TaskState)).setStart(300).setFinish(300)
        profile.getProjectProfile("c").getTaskProfile("c:x").completed(Stub(TaskState)).setStart(300).setFinish(300)
        profile.getProjectProfile("d").getTaskProfile("d:x").completed(Stub(TaskState)).setStart(301).setFinish(302)

        expect:
        profile.projects == [profile.getProjectProfile("a"), profile.getProjectProfile("d"), profile.getProjectProfile("b"), profile.getProjectProfile("c")]
    }

    def "contains build description"() {
        given:
        def param = new StartParameter()
        param.setTaskNames(["foo", "bar"])
        param.setExcludedTaskNames(["one", "two"])

        when:
        profile = new BuildProfile(param)

        then:
        profile.buildDescription.contains(" -x one -x two foo bar")
    }

    def "build description looks nice even if no tasks specified"() {
        given:
        def param = new StartParameter()

        when:
        profile = new BuildProfile(param)

        then:
        profile.buildDescription.contains(" (no tasks specified)")
    }

    def "provides start time description"() {
        when:
        profile.buildStarted = new GregorianCalendar(2010, 1, 1, 12, 25).getTimeInMillis()

        then:
        profile.buildStartedDescription == "Started on: 2010/02/01 - 12:25:00"
    }
}
