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

import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.plugins.internal.HelpBuiltInCommand
import org.gradle.api.specs.Spec
import org.gradle.execution.ProjectSelectionException
import org.gradle.execution.TaskSelectionException
import org.gradle.execution.TaskSelector
import org.gradle.internal.Describables
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.RootBuildState
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Consumer

class DefaultBuildTaskSelectorTest extends Specification {
    def buildRegistry = Mock(BuildStateRegistry)
    def taskSelector = Mock(TaskSelector)
    def selector = new DefaultBuildTaskSelector(
        buildRegistry,
        taskSelector,
        [new HelpBuiltInCommand()],
        TestUtil.problemsService()
    )
    def root = rootBuild()
    def target = root.state

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
        e2.message == message
            .replace("tasks that match", "excluded tasks that match")
            .replace("matching tasks", "matching excluded tasks")

        where:
        path        | message
        ""          | "Cannot locate matching tasks for an empty path. The path should include a task name (for example ':help' or 'help')."
        ":"         | "Cannot locate tasks that match ':'. The path should include a task name (for example ':help' or 'help')."
        "::"        | "Cannot locate tasks that match '::'. The path should include a task name (for example ':help' or 'help')."
        ":::"       | "Cannot locate tasks that match ':::'. The path should include a task name (for example ':help' or 'help')."
        "a::b"      | "Cannot locate tasks that match 'a::b'. The path should not include an empty segment (try 'a:b' instead)."
        "a:b::c"    | "Cannot locate tasks that match 'a:b::c'. The path should not include an empty segment (try 'a:b:c' instead)."
        "a:"        | "Cannot locate tasks that match 'a:'. The path should not include an empty segment (try 'a' instead)."
        "a::"       | "Cannot locate tasks that match 'a::'. The path should not include an empty segment (try 'a' instead)."
        "::a"       | "Cannot locate tasks that match '::a'. The path should not include an empty segment (try ':a' instead)."
        ":::a:b"    | "Cannot locate tasks that match ':::a:b'. The path should not include an empty segment (try ':a:b' instead)."
        " "         | "Cannot locate matching tasks for an empty path. The path should include a task name (for example ':help' or 'help')."
        ": "        | "Cannot locate tasks that match ': '. The path should include a task name (for example ':help' or 'help')."
        "a:  "      | "Cannot locate tasks that match 'a:  '. The path should not include an empty segment (try 'a' instead)."
        "a:  :b"    | "Cannot locate tasks that match 'a:  :b'. The path should not include an empty segment (try 'a:b' instead)."
        "  :a:b"    | "Cannot locate tasks that match '  :a:b'. The path should not include an empty segment (try ':a:b' instead)."
        "  ::::a:b" | "Cannot locate tasks that match '  ::::a:b'. The path should not include an empty segment (try ':a:b' instead)."
    }

    def "selects matching tasks from default project and its subprojects when a name is provided"() {
        when:
        selector.resolveTaskName(null, null, target, "task")

        then:
        1 * taskSelector.getSelection(_, root.defaultProject, "task", true)
    }

    def "selects matching task relative to root project when absolute path is provided"() {
        def project = addProject(root, "lib")
        withIncludedBuilds()

        when:
        selector.resolveTaskName(null, null, target, ":task")

        then:
        1 * taskSelector.getSelection(_, root.rootProject, "task", false)

        when:
        selector.resolveTaskName(null, null, target, ":lib:task")

        then:
        1 * taskSelector.getSelection(_, project, "task", false)
    }

    def "selects matching task relative to default project when relative path is provided"() {
        withIncludedBuilds()

        when:
        selector.resolveTaskName(null, null, target, "proj:task")

        then:
        1 * taskSelector.getSelection(_, root.defaultProject, "task", false)
    }

    def "selects matching task relative to root project of included build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        selector.resolveTaskName(null, null, target, ":build:task")

        then:
        1 * taskSelector.getSelection(_, other.rootProject, "task", false)
    }

    def "can use pattern matching to select project"() {
        withIncludedBuilds()
        def p1 = addProject(root, "someLibs")
        def p2 = addProject(root, "otherLibs")

        when:
        selector.resolveTaskName(null, null, target, ":sL:task")

        then:
        1 * taskSelector.getSelection(_, p1, "task", false)

        when:
        selector.resolveTaskName(null, null, target, "pr:task")

        then:
        1 * taskSelector.getSelection(_, root.defaultProject, "task", false)
    }

    def "fails on unknown project"() {
        withIncludedBuilds(includedBuild("proj1"))
        addProject(root, "proj2")
        addProject(root, "proj3")

        when:
        selector.resolveTaskName(null, null, target, path)

        then:
        def e = thrown(ProjectSelectionException)
        e.message == message

        where:
        path            | message
        "unknown:task"  | "Cannot locate tasks that match 'unknown:task' as project 'unknown' not found in <default project>."
        ":unknown:task" | "Cannot locate tasks that match ':unknown:task' as project 'unknown' not found in <root project>."
        "progx:task"    | "Cannot locate tasks that match 'progx:task' as project 'progx' not found in <default project>. Some candidates are: 'proj'."
    }

    def "fails on ambiguous project"() {
        withIncludedBuilds(includedBuild("proj1"))
        addProject(root, "proj2")
        addProject(root, "proj3")

        when:
        selector.resolveTaskName(null, null, target, ":pr:task")

        then:
        def e = thrown(ProjectSelectionException)
        e.message == "Cannot locate tasks that match ':pr:task' as project 'pr' is ambiguous in <root project>. Candidates are: 'proj', 'proj1', 'proj2', 'proj3'."
    }

    def "lazily resolves an exclude name to tasks in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName(target, "task")

        then:
        filter.build == target

        and:
        0 * target.ensureProjectsConfigured()
        0 * taskSelector._

        when:
        filter.filter.isSatisfiedBy(Stub(Task))

        then:
        1 * taskSelector.getFilter(_, root.defaultProject, "task", true) >> Stub(Spec)
        0 * taskSelector._
    }

    def "lazily resolves an exclude relative path to a task in the target build"() {
        when:
        def filter = selector.resolveExcludedTaskName(target, "proj:task")

        then:
        filter.build == target

        and:
        0 * target.ensureProjectsConfigured()
        0 * taskSelector._

        when:
        filter.filter.isSatisfiedBy(Stub(Task))

        then:
        1 * taskSelector.getFilter(_, root.defaultProject, "task", false) >> Stub(Spec)
        0 * taskSelector._
    }

    def "lazily resolves an exclude absolute path to a task in another build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(target, ":build:task")

        then:
        filter.build == other.state

        and:
        0 * target.ensureProjectsConfigured()
        0 * taskSelector._

        when:
        filter.filter.isSatisfiedBy(Stub(Task))

        then:
        1 * taskSelector.getFilter(_, other.rootProject, "task", false) >> Stub(Spec)
        0 * taskSelector._
    }

    def "lazily resolves an exclude absolute path to a task in the target build when prefix does not match any build"() {
        def other = includedBuild("build")
        withIncludedBuilds(other)

        when:
        def filter = selector.resolveExcludedTaskName(target, ":proj:task")

        then:
        filter.build == target

        and:
        0 * target.ensureProjectsConfigured()
        0 * taskSelector._

        when:
        filter.filter.isSatisfiedBy(Stub(Task))

        then:
        1 * taskSelector.getFilter(_, root.defaultProject, "task", false) >> Stub(Spec)
        0 * taskSelector._
    }

    private void withIncludedBuilds(IncludedBuildFixture... builds) {
        _ * buildRegistry.visitBuilds(_) >> { Consumer visitor ->
            visitor.accept(root.state)
            for (final def build in builds) {
                visitor.accept(build.state)
            }
        }
    }

    private RootBuildFixture rootBuild() {
        def build = Mock(RootBuildState)
        def projects = Mock(BuildProjectRegistry)
        def gradle = Mock(GradleInternal)
        def defaultProject = Mock(ProjectInternal)
        def defaultProjectState = Mock(ProjectState)
        def rootProjectState = Mock(ProjectState)

        build.projectsCreated >> true
        build.mutableModel >> gradle
        gradle.defaultProject >> defaultProject
        defaultProject.owner >> defaultProjectState
        defaultProjectState.name >> "proj"
        defaultProjectState.displayName >> Describables.of("<default project>")
        defaultProjectState.projectPath >> Path.path(":proj")
        defaultProjectState.identityPath >> Path.path(":proj")
        defaultProjectState.owner >> build
        defaultProjectState.childProjects >> [defaultProjectState].toSet()

        buildRegistry.rootBuild >> build

        def rootChildProjects = [].toSet()
        rootChildProjects.add(defaultProjectState)

        build.projects >> projects
        projects.rootProject >> rootProjectState
        projects.allProjects >> [rootProjectState]
        rootProjectState.name >> "root"
        rootProjectState.displayName >> Describables.of("<root project>")
        rootProjectState.projectPath >> Path.ROOT
        rootProjectState.identityPath >> Path.ROOT
        rootProjectState.owner >> build
        rootProjectState.childProjects >> rootChildProjects
        rootProjectState.created >> true

        return new RootBuildFixture(build, rootProjectState, rootChildProjects, defaultProjectState)
    }

    private IncludedBuildFixture includedBuild(String name) {
        def build = Mock(IncludedBuildState)
        def projects = Mock(BuildProjectRegistry)
        def rootProject = Mock(ProjectState)

        _ * build.name >> name
        _ * build.projects >> projects
        _ * build.importableBuild >> true
        _ * build.projectsLoaded >> true
        _ * projects.rootProject >> rootProject
        _ * rootProject.projectPath >> Path.ROOT
        _ * rootProject.identityPath >> Path.path(":${name}")
        _ * rootProject.owner >> build

        return new IncludedBuildFixture(build, rootProject)
    }

    private ProjectState addProject(RootBuildFixture build, String name) {
        def projectState = Mock(ProjectState)
        projectState.projectPath >> Path.path(":$name")
        projectState.identityPath >> Path.path(":$name")
        projectState.owner >> build.state

        build.rootChildProjects.add(projectState)

        return projectState
    }

    class IncludedBuildFixture {
        final IncludedBuildState state
        final ProjectState rootProject

        IncludedBuildFixture(IncludedBuildState state, ProjectState rootProject) {
            this.state = state
            this.rootProject = rootProject
        }
    }

    class RootBuildFixture {
        final RootBuildState state
        final ProjectState rootProject
        final ProjectState defaultProject
        final Set<ProjectState> rootChildProjects

        RootBuildFixture(RootBuildState state, ProjectState rootProject, Set<ProjectState> rootChildProjects, ProjectState defaultProject) {
            this.state = state
            this.rootProject = rootProject
            this.rootChildProjects = rootChildProjects
            this.defaultProject = defaultProject
        }
    }
}
