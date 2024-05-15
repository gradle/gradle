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

package org.gradle.integtests.tooling.r55

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseWorkspace
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import spock.lang.TempDir

@TargetGradleVersion('>=5.5')
class ReservedProjectNamesCrossVersionSpec extends ToolingApiSpecification {

    @TempDir
    File externalProjectFolder

    def setup() {
        buildFile << """
        project(':b') {
            apply plugin: 'eclipse'
            eclipse {
                project.name='explicitName'
            }
        }
        """
        settingsFile << """
        rootProject.name = 'root'
        include ':a', ':b'
        """
    }

    def "externally used project names can be supplied and are deduplicated"() {
        when:
        EclipseProject model = withConnection { connection ->
            return connection.action(new LoadEclipseModel(eclipseWorkspace([
                gradleProject("a"),
                externalProject("a")
            ]))).run()
        }

        then:
        def projects = collectProjects(model)
        !projects.collect({ it.name }).contains("a")
        projects.collect({ it.name }).contains("root-a")
    }

    def "name collisions are resolved"() {
        when:
        EclipseProject model = withConnection { connection ->
            return connection.action(new LoadEclipseModel(eclipseWorkspace([
                externalProject("a")
            ]))).run()
        }

        then:
        def projects = collectProjects(model)
        !projects.collect({ it.name }).contains("a")
        projects.collect({ it.name }).contains("root-a")
    }

    def "gradle projects are recognized and not wrongly deduplicated"() {
        when:
        EclipseProject model = withConnection { connection ->
            return connection.action(new LoadEclipseModel(eclipseWorkspace([
                project("root", projectDir),
                gradleProject("a"),
                project("explicitName", file("b"))
            ]))).run()
        }

        then:
        collectProjects(model).collect({ it.name }).containsAll(["root", "a", "explicitName"])
    }

    def "model can be fetched via connection "() {
        when:
        EclipseProject model = withConnection { connection ->
            connection.getModel(EclipseProject.class)
        }

        then:
        collectProjects(model).collect({ it.name }).contains("a")
    }

    def "model can be fetched without parameters"() {
        when:
        EclipseProject model = withConnection { connection ->
            connection.action(new LoadEclipseModel()).run()
        }

        then:
        collectProjects(model).collect({ it.name }).contains("a")
    }

    def "buildscript names are deduplicated"() {
        when:
        EclipseProject model = withConnection { connection ->
            connection.action(new LoadEclipseModel(eclipseWorkspace([externalProject("explicitName")]))).run()
        }

        then:
        def projects = collectProjects(model)
        projects.collect({ it.name }).contains("root-explicitName")
        !projects.collect({ it.name }).contains("explicitName")
    }

    def "Reserved names work in composite builds"() {
        given:
        settingsFile << """
                includeBuild 'includedBuild1'
                includeBuild 'includedBuild2'
            """
        def inc1 = multiProjectBuildInSubFolder("includedBuild1", ["a", "b", "c"])
        def inc2 = multiProjectBuildInSubFolder("includedBuild2", ["a", "b", "c"])
        def workspace = eclipseWorkspace([
            project("root", projectDir), gradleProject("a"), project("explicitName", file("b")),
            externalProject("root-a"),
            project("includedBuild1", inc1), project("includedBuild1-a", inc1.file("a")), project("includedBuild1-includedBuild1-b", inc1.file("b")), project("includedBuild1-c", inc1.file("c")),
            externalProject("includedBuild1-b"),
            project("includedBuild2", inc2), project("includedBuild2-a", inc2.file("a")), project("includedBuild2-includedBuild2-b", inc2.file("b")), project("includedBuild2-c", inc2.file("c")),
            externalProject("includedBuild2-b"),
        ])

        when:
        def eclipseModels = withConnection { con ->
            def builder = con.action(new SupplyRuntimeAndLoadCompositeEclipseModels(workspace))
            collectOutputs(builder)
            builder.run()
        }

        then:
        eclipseModels.collect {
            collectProjects(it)
        }.flatten().collect {
            it.name
        }.containsAll([
            'root', 'root-root-a', 'explicitName',
            'includedBuild1', 'includedBuild1-a', 'includedBuild1-includedBuild1-b', 'includedBuild1-c',
            'includedBuild2', 'includedBuild2-a', 'includedBuild2-includedBuild2-b', 'includedBuild2-c'])
    }


    Collection<EclipseProject> collectProjects(EclipseProject parent) {
        return parent.children.collect { collectProjects(it) }.flatten() + [parent]
    }

    EclipseWorkspace eclipseWorkspace(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseWorkspace(new File(externalProjectFolder, "workspace").tap { mkdirs() }, projects)
    }

    EclipseWorkspaceProject gradleProject(String name) {
        project(name, file(name))
    }

    EclipseWorkspaceProject project(String name, File location) {
        new DefaultEclipseWorkspaceProject(name, location)
    }

    EclipseWorkspaceProject externalProject(String name) {
        new DefaultEclipseWorkspaceProject(name, new File(externalProjectFolder, name).tap { mkdirs() })
    }

}
