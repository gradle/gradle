/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r84

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

import java.util.function.Function

import static java.util.Arrays.asList

@ToolingApiVersion('>=8.4')
@TargetGradleVersion('>=8.4')
class TestLauncherCompositeBuildCrossVersionTest extends ToolingApiSpecification {

    enum LauncherApi {
        TEST_LAUNCHER,
        BUILD_LAUNCHER
    }

    def "Can run tasks from included builds"() {
        given:
        def runTestClass = withIncludedBuildTest(api)

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        then:
        onlyTestClass2In(output2)

        where:
        api << LauncherApi.values()
    }

    @Issue('https://github.com/gradle/gradle/issues/26206')
    @Issue('https://github.com/gradle/gradle/issues/24550')
    def "Can run tasks from included builds when configuration cache is enabled"() {
        given:
        def runTestClass = withIncludedBuildTest(api)

        and:
        withConfigurationCache()

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        and:
        onlyTestClass2In(output2)

        and:
        if (api == LauncherApi.BUILD_LAUNCHER) {
            // Remove distinction once https://github.com/gradle/gradle/issues/24550 is fixed
            assert noConfigurationCacheAvailableIn(output2)
        } else {
            assert configurationCacheReusedIn(output2)
        }

        where:
        api << LauncherApi.values()
    }

    def "Can run tasks from included build subproject"() {
        given:
        def runTestClass = withIncludedBuildSubprojectTest(api)

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        then:
        onlyTestClass2In(output2)

        where:
        api << LauncherApi.values()
    }

    @Issue('https://github.com/gradle/gradle/issues/26206')
    @Issue('https://github.com/gradle/gradle/issues/24550')
    def "Can run tasks from included build subproject when configuration cache is enabled"() {
        given:
        def runTestClass = withIncludedBuildSubprojectTest(api)

        and:
        withConfigurationCache()

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        and:
        onlyTestClass2In(output2)

        and:
        if (api == LauncherApi.BUILD_LAUNCHER) {
            // Remove distinction once https://github.com/gradle/gradle/issues/24550 is fixed
            assert noConfigurationCacheAvailableIn(output2)
        } else {
            assert configurationCacheReusedIn(output2)
        }

        where:
        api << LauncherApi.values()
    }

    def "Can run tasks from nested included build buildSrc"() {
        given:
        def runTestClass = withIncludedBuildBuildSrcTest(api)

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        and:
        onlyTestClass2In(output2)

        where:
        api << LauncherApi.values()
    }

    @Issue('https://github.com/gradle/gradle/issues/26206')
    def "Can run tasks from nested included build buildSrc when configuration cache is enabled"() {
        given:
        def runTestClass = withIncludedBuildBuildSrcTest(api)

        and:
        withConfigurationCache()

        when:
        def output1 = runTestClass.apply('TestClass1')

        then:
        notThrown(Exception)

        and:
        onlyTestClass1In(output1)

        when:
        def output2 = runTestClass.apply('TestClass2')

        then:
        notThrown(Exception)

        and:
        onlyTestClass2In(output2)

        and:
        if (api == LauncherApi.BUILD_LAUNCHER) {
            // Remove distinction once https://github.com/gradle/gradle/issues/24550 is fixed
            assert noConfigurationCacheAvailableIn(output2)
        } else {
            assert configurationCacheReusedIn(output2)
        }

        where:
        api << LauncherApi.values()
    }

    private Function<String, String> withIncludedBuildTest(LauncherApi api) {
        settingsFile << "includeBuild('app')"
        javaBuildWithTests(file('app'))
        return { testClassName ->
            runTaskAndTestClassUsing(api, ':app:test', testClassName)
        }
    }

    private Function<String, String> withIncludedBuildSubprojectTest(LauncherApi api) {
        settingsFile << "includeBuild('app')"
        file('app/settings.gradle') << "include 'lib'"
        javaLibraryWithTests(file('app/lib'))
        return { testClassName ->
            runTaskAndTestClassUsing(api, ':app:lib:test', testClassName)
        }
    }

    private Function<String, String> withIncludedBuildBuildSrcTest(LauncherApi api) {
        settingsFile << "includeBuild('app')"
        file('app/settings.gradle') << ''
        javaBuildWithTests(file('app/buildSrc'))
        return { testClassName ->
            runTaskAndTestClassUsing(api, ':app:buildSrc:test', testClassName)
        }
    }

    private TestFile withConfigurationCache() {
        file("gradle.properties") << 'org.gradle.configuration-cache=true'
    }

    private String runTaskAndTestClassUsing(LauncherApi api, String task, String testClass) {
        withConnection { ProjectConnection connection ->
            def stdout = new ByteArrayOutputStream()
            def tee = new TeeOutputStream(System.out, stdout)
            switch (api) {
                case LauncherApi.TEST_LAUNCHER:
                    connection.newTestLauncher()
                        .withTaskAndTestClasses(task, asList(testClass))
                        .setStandardOutput(tee)
                        .run()
                    break
                case LauncherApi.BUILD_LAUNCHER:
                    connection.newBuild()
                        .forTasks(task, "--tests", testClass)
                        .setStandardOutput(tee)
                        .run()
                    break
            }
            stdout.toString('utf-8')
        }
    }

    private void onlyTestClass1In(String output) {
        assert output.contains('TestClass1.testMethod')
        assert !output.contains('TestClass2.testMethod')
    }

    private void onlyTestClass2In(String output) {
        assert output.contains('TestClass2.testMethod')
        assert !output.contains('TestClass1.testMethod')
    }

    private void javaBuildWithTests(TestFile projectDir) {
        projectDir.file('settings.gradle') << ''
        javaLibraryWithTests(projectDir)
    }

    private void javaLibraryWithTests(TestFile projectDir) {
        projectDir.file('build.gradle') << '''
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
            tasks.named('test') {
                testLogging {
                    showStandardStreams = true
                }
            }
        '''
        writeTestClass(projectDir, 'TestClass1')
        writeTestClass(projectDir, 'TestClass2')
    }

    private TestFile writeTestClass(TestFile projectDir, String testClassName) {
        projectDir.file("src/test/java/${testClassName}.java") << """
            public class $testClassName {
                @org.junit.jupiter.api.Test
                void testMethod() {
                    System.out.println("${testClassName}.testMethod");
                }
            }
        """
    }

    private boolean configurationCacheReusedIn(String output) {
        output.contains('Reusing configuration cache.')
    }

    private boolean noConfigurationCacheAvailableIn(String output) {
        output.contains('Calculating task graph as no configuration cache is available')
    }
}
