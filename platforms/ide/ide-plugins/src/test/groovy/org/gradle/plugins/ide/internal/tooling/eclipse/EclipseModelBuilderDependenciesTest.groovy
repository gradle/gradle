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
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath
import org.gradle.plugins.ide.eclipse.model.ProjectDependency
import org.gradle.plugins.ide.internal.configurer.EclipseModelAwareUniqueProjectNameProvider
import org.gradle.plugins.ide.internal.tooling.EclipseModelBuilder
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.model.eclipse.EclipseRuntime
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
import spock.lang.Issue

class EclipseModelBuilderDependenciesTest extends AbstractProjectBuilderSpec {
    Project child1
    Project child2
    Project child3
    Project child4
    Project child5

    def setup() {
        child1 = ProjectBuilder.builder().withName("child1").withParent(project).build()
        child2 = ProjectBuilder.builder().withName("child2").withParent(project).build()
        child3 = ProjectBuilder.builder().withName("child3").withParent(project).build()
        child4 = ProjectBuilder.builder().withName("child4").withParent(project).build()
        child5 = ProjectBuilder.builder().withName("child5").withParent(project).build()

        def libsDir = new File(project.projectDir, "libs")
        libsDir.mkdirs()
        new File(libsDir, "test-1.0.jar").createNewFile()
        [project, child1, child2, child3, child4, child5].each { it.pluginManager.apply(EclipsePlugin) }
        [child1, child2, child3, child4, child5].each {
            it.plugins.apply(JavaPlugin)
            it.repositories.flatDir {
                dirs libsDir
            }
        }

        child1.configurations.create("testArtifacts")
        def task = child1.tasks.create("testJar", Jar) {
            archiveClassifier.set("tests")
            from(project.sourceSets.test.output)
        }
        child1.dependencies {
            implementation "fakegroup:test:1.0"
        }
        child1.artifacts.add("testArtifacts", task)
        child2.dependencies {
            implementation child2.dependencies.project(path: ":child1")
            testImplementation child2.dependencies.project(path: ":child1", configuration: "testArtifacts")
        }
        child3.eclipse {
            (classpath as EclipseClasspath).file.whenMerged {
                def customDependency = new ProjectDependency("/child1")
                customDependency.buildDependencies(":child1:jar")
                customDependency.publication = fileReference("customJar.jar")
                customDependency.publicationJavadocPath = fileReference("customJar-javadoc.jar")
                customDependency.publicationSourcePath = fileReference("customJar-sources.jar")
                entries += [customDependency]
            }
        }
        child4.dependencies {
            implementation child1.dependencies.project(path: ":child1")
            implementation "inexistent:dependency:10.0"
            implementation "fakegroup:test:1.0"
            implementation "notreal:depen dency:s p a c e s"
        }
        child5.dependencies {
            testImplementation child5.dependencies.project(path: ":child1")
            testImplementation child5.dependencies.project(path: ":child1", configuration: "testArtifacts")
        }
    }

    def "project dependencies are mapped to eclipse model"() {
        setup:
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        DefaultEclipseProject eclipseChild2 = eclipseModel.children.find { it.name == 'child2' }
        eclipseChild2.projectDependencies.collect { it.path } == ['child1']
        // verify we inherit the transitive dependency
        eclipseChild2.classpath.collect { it.file.name } == ["test-1.0.jar"]
    }

    def "unresolved dependencies are marked as such"() {
        setup:
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        DefaultEclipseProject eclipseChild4 = eclipseModel.children.find { it.name == 'child4' }
        def unresolvedRefs = eclipseChild4.classpath.findAll { !it.resolved }
        unresolvedRefs.size == 2
        unresolvedRefs.stream().allMatch { ref -> ref.file.name.contains("unresolved dependency") }
    }

    def "project dependencies are mapped to eclipse model with supplied runtime"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1), gradleProject(child2)])
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", eclipseRuntime, project)

        then:
        DefaultEclipseProject eclipseChild2 = eclipseModel.children.find { it.name == 'child2' }
        eclipseChild2.projectDependencies.collect { it.path } == ['child1']
        // verify we inherit the transitive dependency
        eclipseChild2.classpath.collect { it.file.name } == ["test-1.0.jar"]
    }

    def "project dependency is replaced when project is closed"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1, false), gradleProject(child2)])
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", eclipseRuntime, project)

        then:
        DefaultEclipseProject eclipseChild2 = eclipseModel.children.find { it.name == 'child2' }
        eclipseChild2.projectDependencies.collect { it.path } == []
        // jars that replace the project dependencies + the transitive dependency from :a
        eclipseChild2.classpath.collect { it.file.name } == ['child1.jar', 'child1-tests.jar', 'test-1.0.jar']
    }

    def "custom project dependencies are replaced when project is closed"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1, false), gradleProject(child2), gradleProject(child3)])
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", eclipseRuntime, project)

        then:
        DefaultEclipseProject eclipseChild3 = eclipseModel.children.find { it.name == 'child3' }
        eclipseChild3.projectDependencies.collect { it.path } == []
        // jars that replace the project dependencies + the transitive dependency from :a
        eclipseChild3.classpath.collect { it.file.name } == ['customJar.jar']
    }

    def "source and javadoc artifacts are passed on for closed projects"() {
        setup:
        def eclipseRuntime = eclipseRuntime([gradleProject(child1, false), gradleProject(child2), gradleProject(child3)])
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", eclipseRuntime, project)

        then:
        DefaultEclipseProject eclipseChild3 = eclipseModel.children.find { it.name == 'child3' }
        eclipseChild3.projectDependencies.collect { it.path } == []
        // jars that replace the project dependencies + the transitive dependency from :a
        eclipseChild3.classpath.collect { it.file.name } == ['customJar.jar']
        eclipseChild3.classpath[0].source.name == 'customJar-sources.jar'
        eclipseChild3.classpath[0].javadoc.name == 'customJar-javadoc.jar'
    }

    @Issue('https://github.com/gradle/gradle/issues/21968')
    def 'prefer project dependency without test attribute when handling duplicate paths'() {
        setup:
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll('org.gradle.tooling.model.eclipse.EclipseProject', project)

        then:
        def projectDependencies = eclipseModel.children.find { it.name == projectName }.projectDependencies
        projectDependencies.collect { it.classpathAttributes.find { it.name == 'test' }?.value } == expectedTestAttributes

        where:
        projectName | expectedTestAttributes
        'child2'    | [ null ]
        'child5'    | ['true']
    }

    private def createEclipseModelBuilder() {
        def gradleProjectBuilder = new GradleProjectBuilder()
        def uniqueProjectNameProvider = Stub(EclipseModelAwareUniqueProjectNameProvider) {
            getUniqueName(_ as Project) >> { Project p -> p.name }
        }
        new EclipseModelBuilder(gradleProjectBuilder, uniqueProjectNameProvider)
    }

    EclipseRuntime eclipseRuntime(List<EclipseWorkspaceProject> projects) {
        new DefaultEclipseRuntime(new DefaultEclipseWorkspace(new File("workspace"), projects))
    }

    EclipseWorkspaceProject gradleProject(Project project, boolean isOpen = true) {
        new DefaultEclipseWorkspaceProject(project.name, project.projectDir, isOpen)
    }

}
