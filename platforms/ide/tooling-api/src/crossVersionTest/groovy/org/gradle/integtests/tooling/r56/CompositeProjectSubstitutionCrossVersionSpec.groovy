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

package org.gradle.integtests.tooling.r56

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseWorkspace
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies

@TargetGradleVersion('>=5.6')
class CompositeProjectSubstitutionCrossVersionSpec extends ToolingApiSpecification {

    TestFile buildA
    TestFile buildB
    TestFile buildC

    def setup() {

        buildA = singleProjectBuildInRootFolder("buildA") {
            buildFile << """
                apply plugin: 'java-library'
                dependencies {
                    testImplementation "org.test:b1:1.0"
                }
            """
            settingsFile << """
                includeBuild 'buildB'
                includeBuild 'buildC'
            """
        }

        buildB = multiProjectBuildInSubFolder("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                }
                project(':b1') {
                    dependencies {
                        testImplementation "org.test:buildC:1.0"
                    }
                }
            """
        }

        buildC = singleProjectBuildInSubfolder("buildC") {
            buildFile << """
                apply plugin: 'java-library'
            """
        }
    }

    def "EclipseProject model has closed project dependencies substituted in composite"() {
        setup:
        def workspace = eclipseWorkspace([project("buildA", buildA),
                                          project("buildB", buildB),
                                          project("buildC", buildC, false),
                                          project("b1", buildB.file("b1")),
                                          project("b2", buildB.file("b2"))])

        when:
        def eclipseProjects = withConnection { connection ->
            connection.action(new ParameterizedLoadCompositeEclipseModels(workspace, EclipseProject)).run()
        }

        then:
        def b1Model = eclipseProjects.collectMany { collectProjects(it) }.find { it.name == "b1" }
        b1Model.projectDependencies.isEmpty()
        b1Model.classpath.collect { it.file.name }.sort() == ['buildC-1.0.jar']
    }

    def "Closed project tasks are run in composite with substitution"() {
        setup:
        def projectsLoadedHandler = new IntermediateResultHandlerCollector<Void>()
        def buildFinishedHandler = new IntermediateResultHandlerCollector<List<EclipseProject>>()
        def out = new ByteArrayOutputStream()
        def workspace = eclipseWorkspace([project("buildA", buildA),
                                          project("buildB", buildB),
                                          project("buildC", buildC, false),
                                          project("b1", buildB.file("b1")),
                                          project("b2", buildB.file("b2"))])

        when:
        withConnection { connection ->
            connection.action().projectsLoaded(new ParameterizedLoadCompositeEclipseModels(workspace, RunClosedProjectBuildDependencies), projectsLoadedHandler)
                .buildFinished(new ParameterizedLoadCompositeEclipseModels(workspace, EclipseProject), buildFinishedHandler)
                .build()
                .setStandardOutput(out)
                .forTasks()
                .run()
        }

        def eclipseProjects = buildFinishedHandler.result

        then:
        def b1Model = eclipseProjects.collectMany { collectProjects(it) }.find { it.name == "b1" }
        b1Model.projectDependencies.isEmpty()
        b1Model.classpath.collect { it.file.name } == ['buildC-1.0.jar']
        taskExecuted(out, ":eclipseClosedDependencies")
        taskExecuted(out, ":buildC:jar")
    }

    private static def taskExecuted(ByteArrayOutputStream out, String taskPath) {
        out.toString().contains("> Task $taskPath")
    }

    Collection<EclipseProject> collectProjects(EclipseProject parent) {
        return parent.children.collect { collectProjects(it) }.flatten() + [parent]
    }

    EclipseWorkspace eclipseWorkspace(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseWorkspace(temporaryFolder.file("workspace"), projects)
    }


    EclipseWorkspaceProject project(String name, File location, boolean isOpen = true) {
        new DefaultEclipseWorkspaceProject(name, location, isOpen)
    }

    EclipseWorkspaceProject externalProject(String name) {
        new DefaultEclipseWorkspaceProject(name, temporaryFolder.file("external/$name"))
    }
}
