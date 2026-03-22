/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.Task
import org.gradle.util.Path
import spock.lang.Specification

class ProjectScopedCachingTaskDependencyResolveContextTest extends Specification {

    // Build path ":" (root build), current project ":projectA", identity path ":projectA"
    def buildPath = Path.path(":")
    def currentProjectPath = Path.path(":projectA")
    def currentProjectIdentityPath = Path.path(":projectA")

    List<DeferredCrossProjectDependency> capturedDeps = []

    def context = new ProjectScopedCachingTaskDependencyResolveContext<Task>(
        [WorkDependencyResolver.TASK_AS_TASK],
        buildPath,
        currentProjectPath,
        currentProjectIdentityPath,
        { dep, task ->
            capturedDeps.add(dep)
            return Mock(Task) // placeholder
        }
    )

    def "deferCrossProjectResolution defers when target project differs from current"() {
        when:
        def deferred = context.deferCrossProjectResolution(Path.path(":projectB"), "compile")

        then:
        deferred
        capturedDeps.size() == 1
        def capturedDependency = capturedDeps[0] as DeferredCrossProjectDependency.ByProjectTask
        capturedDependency.targetProjectIdentityPath == Path.path(":projectB")
        capturedDependency.taskName == "compile"
    }

    def "deferCrossProjectResolution does not defer when target is current project"() {
        when:
        def deferred = context.deferCrossProjectResolution(Path.path(":projectA"), "compile")

        then:
        !deferred
        capturedDeps.isEmpty()
    }

    def "deferCrossProjectResolution defers cross-project path '#taskPath' with target '#expectedTarget:#expectedTask'"() {
        when:
        def deferred = context.deferCrossProjectResolution(Path.path(taskPath))

        then:
        deferred
        capturedDeps.size() == 1
        def capturedDependency = capturedDeps[0] as DeferredCrossProjectDependency.ByProjectTask
        capturedDependency.targetProjectIdentityPath == Path.path(expectedTarget)
        capturedDependency.taskName == expectedTask

        where:
        taskPath                   | expectedTarget | expectedTask
        // Absolute paths to other projects
        ":projectB:compile"        | ":projectB"    | "compile"
        ":sub:deep:test"           | ":sub:deep"    | "test"
        // Absolute path to root project
        ":compile"                 | ":"            | "compile"
        // Relative path to sibling project (resolved relative to current project :projectA)
        "sub:compile"              | ":projectA:sub" | "compile"
    }

    def "deferCrossProjectResolution does not defer same-project path '#taskPath'"() {
        when:
        def deferred = context.deferCrossProjectResolution(Path.path(taskPath))

        then:
        !deferred
        capturedDeps.isEmpty()

        where:
        taskPath            | _
        // Relative single-segment path — always current project
        "compile"           | _
        // Absolute path targeting current project
        ":projectA:compile" | _
    }

    def 'deferAllProjectsSearch always defers'() {
        given:
        def action = { TaskDependencyResolveContext ctx -> } as java.util.function.Consumer

        when:
        def deferred = context.deferAllProjectsSearch(action)

        then:
        deferred
        capturedDeps.size() == 1
        capturedDeps[0] instanceof DeferredCrossProjectDependency.AllProjectsSearch
    }

    def "getDependencies merges placeholders into result set"() {
        given:
        def realTask = Mock(Task)
        def dependency = Mock(TaskDependencyContainerInternal) {
            visitDependencies(_) >> { TaskDependencyResolveContext ctx ->
                // Add a real task dependency
                ctx.add(realTask)
                // Add a deferred cross-project dependency (will become a placeholder)
                ctx.add(new DeferredCrossProjectDependency.ByProjectTask(Path.path(":projectB"), "compile"))
            }
        }

        when:
        def result = context.getDependencies(null, dependency)

        then:
        result.size() == 2
        result.contains(realTask)
        capturedDeps.size() == 1
    }

    def "getDependencies returns unmodified result when no placeholders are created"() {
        given:
        def realTask = Mock(Task)
        def dependency = Mock(TaskDependencyContainerInternal) {
            visitDependencies(_) >> { TaskDependencyResolveContext ctx ->
                ctx.add(realTask)
            }
        }

        when:
        def result = context.getDependencies(null, dependency)

        then:
        result.size() == 1
        result.contains(realTask)
        capturedDeps.isEmpty()
    }

    def "deferCrossProjectResolution computes correct identity path in non-root build"() {
        given:
        // Build path ":includedBuild", current project ":app", identity path ":includedBuild:app"
        def nonRootContext = new ProjectScopedCachingTaskDependencyResolveContext<Task>(
            [WorkDependencyResolver.TASK_AS_TASK],
            Path.path(":includedBuild"),
            Path.path(":app"),
            Path.path(":includedBuild:app"),
            { dep, task ->
                capturedDeps.add(dep)
                return Mock(Task)
            }
        )

        when:
        def deferred = nonRootContext.deferCrossProjectResolution(Path.path(":lib:compile"))

        then:
        deferred
        // Build path ":includedBuild" + project path ":lib" = ":includedBuild:lib"
        def capturedDependency = capturedDeps[0] as DeferredCrossProjectDependency.ByProjectTask
        capturedDependency.targetProjectIdentityPath == Path.path(":includedBuild:lib")
        capturedDependency.taskName == "compile"
    }
}
