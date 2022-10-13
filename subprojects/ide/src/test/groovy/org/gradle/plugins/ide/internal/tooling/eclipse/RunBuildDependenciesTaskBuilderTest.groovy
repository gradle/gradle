/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.eclipse

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.internal.tooling.RunBuildDependenciesTaskBuilder
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.model.eclipse.EclipseRuntime
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject

class RunBuildDependenciesTaskBuilderTest extends AbstractProjectBuilderSpec {
    Project child1
    Project child2

    def setup() {
        child1 = ProjectBuilder.builder().withName("child1").withParent(project).build()
        child2 = ProjectBuilder.builder().withName("child2").withParent(project).build()

        [project, child1, child2].each { it.pluginManager.apply(EclipsePlugin) }
        [child1, child2].each {
            it.plugins.apply(JavaPlugin)
        }

        child1.configurations.create("testArtifacts")
        def task = child1.tasks.create("testJar", Jar) {
            archiveClassifier.set("tests")
            from(project.sourceSets.test.output)
        }
        child1.artifacts.add("testArtifacts", task)
        child2.dependencies {
            implementation child2.dependencies.project(path: ":child1")
            testImplementation child2.dependencies.project(path: ":child1", configuration: "testArtifacts")
        }
    }

    def "closed project build dependencies are executed on build"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1, false), gradleProject(child2)])
        def modelBuilder = createBuilder()

        when:
        modelBuilder.buildAll("org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies", eclipseRuntime, project)

        then:
        def buildTask = project.tasks.getByName("eclipseClosedDependencies")
        buildTask != null
        buildTask.taskDependencies.getDependencies(buildTask).collect { it.path }.sort() == ([':child1:jar', ':child1:testJar'])
        project.getGradle().getStartParameter().getTaskNames() == ['eclipseClosedDependencies']
    }

    def "build task is not added if no projects are closed"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1), gradleProject(child2)])
        def modelBuilder = createBuilder()

        when:
        modelBuilder.buildAll("org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies", eclipseRuntime, project)

        then:
        project.tasks.findByName("eclipseClosedDependencies") == null
        project.getGradle().getStartParameter().getTaskNames().isEmpty()
    }

    def "task name is deduplicated"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1, false), gradleProject(child2)])
        project.tasks.create("eclipseClosedDependencies")
        def modelBuilder = createBuilder()

        when:
        modelBuilder.buildAll("org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies", eclipseRuntime, project)

        then:
        def buildTask = project.tasks.getByName("eclipseClosedDependencies_")
        buildTask != null
        buildTask.taskDependencies.getDependencies(buildTask).collect { it.path }.sort() == ([':child1:jar', ':child1:testJar'])
        project.getGradle().getStartParameter().getTaskNames() == ['eclipseClosedDependencies_']
    }


    private static def createBuilder() {
        new RunBuildDependenciesTaskBuilder()
    }

    EclipseRuntime eclipseRuntime(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseRuntime(new DefaultEclipseWorkspace(new File("workspace"), projects))
    }

    EclipseWorkspaceProject gradleProject(Project project, boolean isOpen = true) {
        new DefaultEclipseWorkspaceProject(project.name, project.projectDir, isOpen)
    }

}
