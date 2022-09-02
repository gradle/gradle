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

import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import spock.lang.Specification

class DefaultBuildTaskSelectorTest extends Specification {
    def buildRegistry = Mock(BuildStateRegistry)
    def selector = new DefaultBuildTaskSelector(buildRegistry)
    def target = build(":")

    def "resolves a name to tasks in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName("task", target)

        then:
        filter.build == target
        filter.filter != null
    }

    def "resolves a relative path to a task in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName("proj:task", target)

        then:
        filter.build == target
        filter.filter != null
    }

    def "resolves an absolute path to a task in another build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(":build:task", target)

        then:
        filter.build == other
        filter.filter != null
    }

    def "resolves an absolute path to a task in the target build when prefix does not match any build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(":proj:task", target)

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
