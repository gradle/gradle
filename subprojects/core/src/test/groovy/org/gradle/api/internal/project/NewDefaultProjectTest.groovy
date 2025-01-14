/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.api.InvalidUserDataException
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.util.TestUtil.createChildProject

class NewDefaultProjectTest extends AbstractProjectBuilderSpec {
    def "provides all tasks recursively"() {
        def a = createChildProject(project, "a")

        [project, a].each { it.task "foo"; it.task "bar" }

        when:
        def rootTasks = project.getAllTasks(true)
        def aTasks = a.getAllTasks(true)

        then:
        rootTasks.size() == 2
        rootTasks[project]*.path as Set == [":bar", ":artifactTransforms", ":buildEnvironment", ":components", ":dependencies", ":dependencyInsight", ":dependentComponents", ":foo", ":help", ":init", ":javaToolchains", ":model", ":outgoingVariants", ":projects", ":properties", ":resolvableConfigurations", ":tasks", ":wrapper", ":foo"] as Set
        rootTasks[a]*.path as Set == [":a:artifactTransforms", ":a:bar", ":a:buildEnvironment", ":a:components", ":a:dependencies", ":a:dependencyInsight", ":a:dependentComponents", ":a:foo", ":a:help", ":a:javaToolchains", ":a:model", ":a:outgoingVariants", ":a:projects", ":a:properties", ":a:resolvableConfigurations", ":a:tasks", ":a:foo"] as Set

        aTasks.size() == 1
        aTasks[a]*.path as Set == [":a:artifactTransforms", ":a:bar", ":a:buildEnvironment", ":a:components", ":a:dependencies", ":a:dependencyInsight", ":a:dependentComponents", ":a:foo", ":a:help", ":a:javaToolchains", ":a:model", ":a:outgoingVariants", ":a:projects", ":a:properties", ":a:resolvableConfigurations", ":a:tasks", ":a:foo"] as Set
    }

    def "provides all tasks non-recursively"() {
        def a = createChildProject(project, "a")

        [project, a].each { it.task "foo"; it.task "bar" }

        when:
        def rootTasks = project.getAllTasks(false)
        def aTasks = a.getAllTasks(false)

        then:
        rootTasks.size() == 1
        rootTasks[project]*.path as Set == [":bar", ":artifactTransforms", ":buildEnvironment", ":components", ":dependencies", ":dependencyInsight", ":dependentComponents", ":foo", ":help", ":init", ":javaToolchains", ":model", ":outgoingVariants", ":projects", ":properties", ":resolvableConfigurations", ":tasks", ":wrapper", ":foo"] as Set

        aTasks.size() == 1
        aTasks[a]*.path as Set == [":a:bar", ":a:artifactTransforms", ":a:buildEnvironment", ":a:components", ":a:dependencies", ":a:dependencyInsight", ":a:dependentComponents", ":a:foo", ":a:help", ":a:javaToolchains", ":a:model", ":a:outgoingVariants", ":a:projects", ":a:properties", ":a:resolvableConfigurations", ":a:tasks", ":a:foo"] as Set
    }

    def "provides task by name recursively"() {
        def a = createChildProject(project, "a")

        [project, a].each { it.task "foo"; it.task "bar" }

        when:
        def rootTasks = project.getTasksByName("foo", true)
        def aTasks = a.getTasksByName("bar", true)

        then:
        rootTasks*.path as Set == [":a:foo", ":foo"] as Set
        aTasks*.path == [":a:bar"]
    }

    def "provides task by name non-recursively"() {
        def a = createChildProject(project, "a")

        [project, a].each { it.task "foo"; it.task "bar" }

        when:
        def rootTasks = project.getTasksByName("foo", false)
        def aTasks = a.getTasksByName("bar", false)

        then:
        rootTasks*.path == [":foo"]
        aTasks*.path == [":a:bar"]
    }

    def "does not allow asking for tasks using empty name"() {
        when: project.getTasksByName('', true)
        then: thrown(InvalidUserDataException)

        when: project.getTasksByName(null, true)
        then: thrown(InvalidUserDataException)
    }

    def "allows asking for unknown tasks"() {
        project.task "bar"

        expect:
        project.getTasksByName('foo', true).empty
        project.getTasksByName('foo', false).empty
    }
}
