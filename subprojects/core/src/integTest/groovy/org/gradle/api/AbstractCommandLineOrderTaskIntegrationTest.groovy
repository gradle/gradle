/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractCommandLineOrderTaskIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    enum ProductionType { OUTPUT, LOCAL_STATE }

    BuildFixture rootBuild = new RootBuild(testDirectory)

    void writeAllFiles() {
        rootBuild.writeFiles()
    }

    ProjectFixture subproject(String path) {
        return rootBuild.subproject(path)
    }

    BuildFixture includedBuild(String name) {
        return rootBuild.includedBuild(name)
    }

    class RootBuild extends BuildFixture {
        Map<String, BuildFixture> builds = [:]

        RootBuild(TestFile rootDirectory) {
            super('root', rootDirectory)
        }

        BuildFixture includedBuild(String name) {
            return builds.get(name, new BuildFixture(name, buildDir.file(name)))
        }

        TaskFixture task(String name) {
            return subproject(':').task(name)
        }

        void writeFiles() {
            super.writeFiles()
            settingsFile << """
                ${builds.keySet().collect { "includeBuild " + quote(it) }.join('\n')}
            """.stripIndent()
            builds.values().each { it.writeFiles() }
        }
    }

    class BuildFixture {
        final String name
        final TestFile buildDir
        Map<String, ProjectFixture> projects = [:]

        BuildFixture(String name, TestFile buildDir) {
            this.name = name
            this.buildDir = buildDir
        }

        ProjectFixture subproject(String path) {
            return projects.get(path, new ProjectFixture(this, path))
        }

        void writeFiles() {
            buildDir.file('settings.gradle') << """
                rootProject.name = ${quote(name)}
                include ${projects.keySet().collect { quote(it) }.join(', ')}
            """.stripIndent()
            projects.values().each { it.writeFiles() }
        }
    }

    class ProjectFixture {
        final BuildFixture build
        final String path
        final Map<String, TaskFixture> tasks = [:]

        ProjectFixture(BuildFixture build, String path) {
            this.build = build
            this.path = path
        }

        TaskFixture task(String name) {
            return tasks.get(name, new TaskFixture(this, taskPath(name)))
        }

        String taskPath(String name) {
            return path == ':' ? ":${name}" : "${path}:${name}"
        }

        TestFile getProjectDir() {
            return build.buildDir.createDir(projectDirRelativePath)
        }

        String getProjectDirRelativePath() {
            return path.replaceAll(':', '/')
        }

        TestFile getBuildFile() {
            return projectDir.file('build.gradle')
        }

        void writeFiles() {
            projectDir.file('build.gradle') << """
                ${tasks.collect { name, task -> task.getConfig() }.join('\n') }
            """.stripIndent()
        }
    }

    class TaskFixture {
        final ProjectFixture project
        final String path
        final Set<TaskFixture> dependencies = []
        final Set<TaskFixture> finalizers = []
        final Set<TaskFixture> mustRunAfter = []
        final Set<TaskFixture> shouldRunAfter = []
        final Set<String> destroys = []
        final Set<String> produces = []
        final Set<String> localState = []
        final Set<String> inputFiles = []
        boolean shouldBlock
        String failMessage

        TaskFixture(ProjectFixture project, String path) {
            this.project = project
            this.path = path
        }

        TaskFixture dependsOn(TaskFixture dependency) {
            dependencies.add(dependency)
            return this
        }

        TaskFixture shouldRunAfter(TaskFixture dependency) {
            shouldRunAfter.add(dependency)
            return this
        }

        TaskFixture finalizedBy(TaskFixture finalizer) {
            finalizers.add(finalizer)
            return this
        }

        TaskFixture mustRunAfter(TaskFixture task) {
            mustRunAfter.add(task)
            return this
        }

        TaskFixture destroys(String path) {
            destroys.add(path)
            return this
        }

        TaskFixture outputs(String path) {
            return produces(path, ProductionType.OUTPUT)
        }

        TaskFixture produces(String path, ProductionType type) {
            if (type == ProductionType.OUTPUT) {
                produces.add(path)
            } else if (type == ProductionType.LOCAL_STATE) {
                localState.add(path)
            } else {
                throw new IllegalArgumentException()
            }
            return this
        }

        TaskFixture localState(String path) {
            produces(path, ProductionType.LOCAL_STATE)
            return this
        }

        TaskFixture inputFiles(String path) {
            inputFiles.add(path)
            return this
        }

        String getName() {
            return path.split(':').last()
        }

        String getFullPath() {
            return project.build == rootBuild ? path : ":${project.build.name}${path}"
        }

        TaskFixture shouldBlock() {
            shouldBlock = true
            return this
        }

        TaskFixture fail(String message = 'BOOM') {
            failMessage = message
            return this
        }

        String getConfig() {
            return """
                tasks.register('${name}') {
                    ${dependencies.collect {'dependsOn ' + dependencyFor(it) }.join('\n\t\t\t\t')}
                    ${finalizers.collect { 'finalizedBy ' + dependencyFor(it) }.join('\n\t\t\t\t')}
                    ${mustRunAfter.collect { 'mustRunAfter ' + dependencyFor(it) }.join('\n\t\t\t\t')}
                    ${shouldRunAfter.collect { 'shouldRunAfter ' + dependencyFor(it) }.join('\n\t\t\t\t')}
                    ${produces.collect { 'outputs.file file(' + quote(it) + ')' }.join('\n\t\t\t\t')}
                    ${destroys.collect { 'destroyables.register file(' + quote(it) + ')' }.join('\n\t\t\t\t')}
                    ${localState.collect { 'localState.register file(' + quote(it) + ')' }.join('\n\t\t\t\t')}
                    ${inputFiles.collect { 'inputs.files ' + it }.join('\n\t\t\t\t')}
                    doLast {
                        ${shouldBlock ? server.callFromTaskAction(path) : ''}
                        ${failMessage ? "throw new RuntimeException('$failMessage')" : ''}
                    }
                }
            """.stripIndent()
        }

        String dependencyFor(TaskFixture task) {
            if (task.project.build == this.project.build) {
                return quote(task.path)
            } else {
                return "gradle.includedBuild(${quote(task.project.build.name)}).task(${quote(task.path)})"
            }
        }
    }

    static String quote(String text) {
        return "'${text}'"
    }
}
