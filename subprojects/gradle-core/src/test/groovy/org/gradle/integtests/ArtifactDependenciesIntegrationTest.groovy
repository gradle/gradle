package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*

class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = getTestBuildFile("projectWithConflicts.gradle");
        getTestBuildFile("projectA-1.2-ivy.xml");
        getTestBuildFile("projectB-1.5-ivy.xml");
        getTestBuildFile("projectB-2.1.5-ivy.xml");
        testFile("projectA-1.2.jar").touch();
        testFile("projectB-1.5.jar").touch();
        testFile("projectB-2.1.5.jar").touch();

        usingBuildFile(buildFile).withDependencyList().run();
    }

    @Test
    public void canNestModules() throws IOException {
        File buildFile = getTestBuildFile("projectWithNestedModules.gradle");
        testFile("projectA-1.2.jar").touch();
        testFile("projectB-1.5.jar").touch();
        testFile("projectC-2.0.jar").touch();

        usingBuildFile(buildFile).run();
    }

    @Test
    public void reportsUnknownDependencyError() {
        File buildFile = getTestBuildFile("projectWithUnknownDependency.gradle");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration 'compile'"));
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
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration 'compile'"));
        failure.assertThatCause(containsString("a module is not authorized to depend on itself"));
    }

    @Test
    public void canSpecifyProducerTasksForFileDependency() {
        testFile("settings.gradle").write("include 'sub'");
        testFile("build.gradle") << '''
            configurations { compile }
            dependencies { compile project(path: ':sub', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << { assertTrue(file('sub/sub.jar').isFile()) }
'''
        testFile("sub/build.gradle") << '''
            usePlugin org.gradle.api.plugins.BasePlugin
            configurations { compile }
            dependencies { compile files('sub.jar') { builtBy 'jar' } }
            task jar << { file('sub.jar').text = 'content' }
'''

        inTestDirectory().withTasks("test").run().assertTasksExecuted(":sub:jar", ":sub:uploadCompileInternal", ":test");
    }

    @Test
    public void projectArtifactsContainProjectVersionNumber() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            usePlugin('base')
            configurations { compile }
            task aJar(type: Jar) { }
            artifacts { compile aJar }
'''
        testFile('b/build.gradle') << '''
            usePlugin('base')
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            bJar.doFirst { project.version = 'late' }
            artifacts { compile bJar }
'''
        testFile('build.gradle') << '''
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile'), project(path: ':b', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assertEquals(configurations.compile.collect { it.name }, ['a.jar', 'b-late.jar'])
            }
'''
        inTestDirectory().withTasks('test').run()
    }

}
