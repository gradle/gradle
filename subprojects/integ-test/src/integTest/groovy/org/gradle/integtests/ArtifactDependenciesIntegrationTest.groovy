/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.util.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.gradle.integtests.fixtures.*
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()
    @Rule
    public final HttpServer server = new HttpServer()

    @Before
    public void setup() {
        distribution.requireOwnUserHomeDir()
    }

    @Test
    public void canResolveDependenciesFromAFlatDir() {
        File buildFile = testFile("projectWithFlatDir.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canHaveConfigurationHierarchy() {
        File buildFile = testFile("projectWithConfigurationHierarchy.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = testFile("projectWithConflicts.gradle");
        usingBuildFile(buildFile).run();
        usingBuildFile(buildFile).withDependencyList().run();
    }

    @Test
    public void canNestModules() throws IOException {
        File buildFile = testFile("projectWithNestedModules.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canHaveCycleInDependencyGraph() throws IOException {
        File buildFile = testFile("projectWithCyclesInDependencyGraph.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void canUseDynamicVersions() throws IOException {
        File buildFile = testFile("projectWithDynamicVersions.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void resolutionFailsWhenProjectHasNoRepositoriesEvenWhenArtifactIsCachedLocally() {
        testFile('settings.gradle') << 'include "a", "b"'
        testFile('build.gradle') << """
project(':a') {
    repositories {
        mavenRepo urls: '${repo().rootDir.toURI()}'
    }
    configurations {
        compile
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
project(':b') {
    configurations {
        compile
    }
    dependencies {
        compile 'org.gradle.test:external1:1.0'
    }
}
subprojects {
    task listDeps << { configurations.compile.each { } }
}
"""
        repo().module('org.gradle.test', 'external1', '1.0').publishArtifact()

        inTestDirectory().withTasks('a:listDeps').run()
        def result = inTestDirectory().withTasks('b:listDeps').runWithFailure()
        result.assertThatCause(containsString('unresolved dependency: org.gradle.test#external1;1.0: not found'))
    }

    @Test
    public void reportsUnknownDependencyError() {
        File buildFile = testFile("projectWithUnknownDependency.gradle");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectA;1.2: not found"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectB;2.1.5: not found"));
    }

    @Test
    public void reportsProjectDependsOnSelfError() {
        TestFile buildFile = testFile("build.gradle");
        buildFile << '''
            configurations { compile }
            dependencies { compile project(':') }
            defaultTasks 'listJars'
            task listJars << { configurations.compile.each { println it } }
'''
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("a module is not authorized to depend on itself"));
    }

    @Test
    public void canSpecifyProducerTasksForFileDependency() {
        testFile("settings.gradle").write("include 'sub'");
        testFile("build.gradle") << '''
            configurations { compile }
            dependencies { compile project(path: ':sub', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert file('sub/sub.jar').isFile()
            }
'''
        testFile("sub/build.gradle") << '''
            configurations { compile }
            dependencies { compile files('sub.jar') { builtBy 'jar' } }
            task jar << { file('sub.jar').text = 'content' }
'''

        inTestDirectory().withTasks("test").run().assertTasksExecuted(":sub:jar", ":test");
    }

    @Test
    public void resolvedProjectArtifactsContainProjectVersionInTheirNames() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task aJar(type: Jar) { }
            artifacts { compile aJar }
'''
        testFile('b/build.gradle') << '''
            apply plugin: 'base'
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile bJar }
'''
        testFile('build.gradle') << '''
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile'), project(path: ':b', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert configurations.compile.collect { it.name } == ['a.jar', 'b-late.jar']
            }
'''
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void canUseArtifactSelectorForProjectDependencies() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { 'default' {} }
            task aJar(type: Jar) { }
            artifacts { 'default' aJar }
'''
        testFile('b/build.gradle') << '''
            configurations { compile }
            dependencies { compile(project(':a')) { artifact { name = 'a'; type = 'jar' } } }
            task test {
                inputs.files configurations.compile
                doFirst {
                    assert [project(':a').tasks.aJar.archivePath] as Set == configurations.compile.files
                }
            }
'''
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void canHaveCycleInProjectDependencies() {
        inTestDirectory().withTasks('listJars').run();
    }

    @Test
    public void canHaveNonTransitiveProjectDependencies() {
        repo().module("group", "externalA", 1.5).publishArtifact()

        testFile('settings.gradle') << "include 'a', 'b'"

        testFile('build.gradle') << '''
allprojects {
    apply plugin: 'java'
    repositories { mavenRepo urls: rootProject.uri('repo') }
}
project(':a') {
    dependencies {
        compile 'group:externalA:1.5'
        compile files('libs/externalB.jar')
    }
}
project(':b') {
    configurations.compile.transitive = false
    dependencies {
        compile project(':a') { transitive = false }
    }
    task listJars << {
        assert configurations.compile.collect { it.name } == ['a.jar']
    }
}
'''

        inTestDirectory().withTasks('listJars').run()
    }

    @Test
    public void canResolveAndCacheDependenciesFromHttpIvyRepository() {
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publishArtifact()

        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)
        server.start()

        testFile("build.gradle") << """
apply plugin: 'java'
repositories {
    ivy {
        name = 'gradleReleases'
        artifactPattern "http://localhost:${server.port}/repo/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        inTestDirectory().withTasks('listJars').run()
        inTestDirectory().withTasks('listJars').run()
    }
    
    @Test
    public void reportsMissingAndFailedHttpDownload() {
        server.start()

        testFile("build.gradle") << """
apply plugin: 'java'
repositories {
    ivy {
        name = 'gradleReleases'
        artifactPattern "http://localhost:${server.port}/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
    }
}
dependencies {
    compile 'group:org:1.2'
}
task show << { println configurations.compile.files }
"""

        def result = executer.withTasks("show").runWithFailure()
        result.assertHasDescription('Execution failed for task \':show\'.')
        result.assertHasCause('Could not resolve all dependencies for configuration \':compile\':')
        assert result.getOutput().contains('group#org;1.2: not found')

        server.addBroken('/')

        result = executer.withTasks("show").runWithFailure()
        result.assertHasDescription('Execution failed for task \':show\'.')
        result.assertHasCause('Could not resolve all dependencies for configuration \':compile\':')
    }

    MavenRepository repo() {
        return new MavenRepository(testFile('repo'))
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(testFile('ivy-repo'))
    }
}

