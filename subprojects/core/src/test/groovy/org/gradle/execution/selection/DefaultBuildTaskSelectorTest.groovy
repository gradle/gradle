/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.selection

import org.gradle.api.plugins.internal.HelpBuiltInCommand
import org.gradle.execution.TaskSelectionException
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import spock.lang.Specification

class DefaultBuildTaskSelectorTest extends Specification {
    def buildRegistry = Mock(BuildStateRegistry)
    def selector = new DefaultBuildTaskSelector(buildRegistry, [new HelpBuiltInCommand()])
    def target = build(":")

    def "fails on badly formed path"() {
        when:
        selector.resolveTaskName(null, null, target, path)

        then:
        def e = thrown(TaskSelectionException)
        e.message == message

        when:
        selector.resolveExcludedTaskName(target, path)

        then:
        def e2 = thrown(TaskSelectionException)
        e2.message == message.replace("matching tasks", "matching excluded tasks")

        where:
        path        | message
        ""          | "Cannot locate matching tasks for an empty path. The path should include a task name (for example ':help' or 'help')."
        ":"         | "Cannot locate matching tasks for path ':'. The path should include a task name (for example ':help' or 'help')."
        "::"        | "Cannot locate matching tasks for path '::'. The path should include a task name (for example ':help' or 'help')."
        ":::"       | "Cannot locate matching tasks for path ':::'. The path should include a task name (for example ':help' or 'help')."
        "a::b"      | "Cannot locate matching tasks for path 'a::b'. The path should not include an empty segment (try 'a:b' instead)."
        "a:b::c"    | "Cannot locate matching tasks for path 'a:b::c'. The path should not include an empty segment (try 'a:b:c' instead)."
        "a:"        | "Cannot locate matching tasks for path 'a:'. The path should not include an empty segment (try 'a' instead)."
        "a::"       | "Cannot locate matching tasks for path 'a::'. The path should not include an empty segment (try 'a' instead)."
        "::a"       | "Cannot locate matching tasks for path '::a'. The path should not include an empty segment (try ':a' instead)."
        ":::a:b"    | "Cannot locate matching tasks for path ':::a:b'. The path should not include an empty segment (try ':a:b' instead)."
        " "         | "Cannot locate matching tasks for an empty path. The path should include a task name (for example ':help' or 'help')."
        ": "        | "Cannot locate matching tasks for path ': '. The path should include a task name (for example ':help' or 'help')."
        "a:  "      | "Cannot locate matching tasks for path 'a:  '. The path should not include an empty segment (try 'a' instead)."
        "a:  :b"    | "Cannot locate matching tasks for path 'a:  :b'. The path should not include an empty segment (try 'a:b' instead)."
        "  :a:b"    | "Cannot locate matching tasks for path '  :a:b'. The path should not include an empty segment (try ':a:b' instead)."
        "  ::::a:b" | "Cannot locate matching tasks for path '  ::::a:b'. The path should not include an empty segment (try ':a:b' instead)."
    }

    def "resolves an exclude name to tasks in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName(target, "task")

        then:
        filter.build == target
        filter.filter != null
    }

    def "resolves an exclude relative path to a task in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName(target, "proj:task")

        then:
        filter.build == target
        filter.filter != null
    }

    def "resolves an exclude absolute path to a task in another build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(target, ":build:task")

        then:
        filter.build == other
        filter.filter != null
    }

    def "resolves an exclude absolute path to a task in the target build when prefix does not match any build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(target, ":proj:task")

        then:
        filter.build == target
        filter.filter != null
    }

    private void withIncludedBuilds(IncludedBuildState... builds) {
        _ * buildRegistry.includedBuilds >> builds.toList()
    }

    private BuildState build(String name) {
        def build = Mock(BuildState)
        return build
    }

    private IncludedBuildState includedBuild(String name) {
        def build = Mock(IncludedBuildState)
        _ * build.name >> name
        return build
    }
}
