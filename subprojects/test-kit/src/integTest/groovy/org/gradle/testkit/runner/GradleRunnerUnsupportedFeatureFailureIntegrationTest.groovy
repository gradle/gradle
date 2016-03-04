/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.executer.ForkingGradleExecuter
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.fixtures.AutomaticClasspathInjectionFixture
import org.gradle.testkit.runner.fixtures.annotations.Debug
import org.gradle.testkit.runner.fixtures.annotations.NonCrossVersion
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@NonCrossVersion
class GradleRunnerUnsupportedFeatureFailureIntegrationTest extends GradleRunnerIntegrationTest {

    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()
    private final AutomaticClasspathInjectionFixture fixture = new AutomaticClasspathInjectionFixture()

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "fails informatively when trying to inspect executed tasks with unsupported gradle version"() {
        String maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.CAPTURE_BUILD_RESULT_TASKS.since)
        String minSupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_TASKS.since.version

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        and:
        result.tasks

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."

        when:
        result.task(":foo")

        then:
        e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."

        when:
        result.tasks(SUCCESS)

        then:
        e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    @Debug
    def "fails informatively when trying to inspect build output in debug mode with unsupported gradle version"() {
        String maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since)
        String minSupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since.version

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture build output in debug mode with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    def "fails informatively when trying to inject plugin classpath with unsupported gradle version"() {
        String maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since)
        String minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since.version

        given:
        buildFile << pluginDeclaration()

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .withPluginClasspath([file("foo")])
            .build()

        then:
        def e = thrown InvalidRunnerConfigurationException
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support the plugin classpath injection feature used by GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    def "plugin classpath is not injected automatically if target Gradle version does not support feature"() {
        given:
        String maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since)
        String minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since.version

        File projectDir = file('sampleProject')
        List<File> pluginClasspath = fixture.getPluginClasspath(projectDir)
        File pluginClasspathFile = fixture.createPluginClasspathManifestFile(projectDir, pluginClasspath)
        compilePluginProjectSources(projectDir)
        buildFile << pluginDeclaration()

        when:
        fixture.withClasspath([pluginClasspathFile.parentFile]) {
            runner('helloWorld')
                .withGradleVersion(maxUnsupportedVersion)
                .withPluginClasspath()
                .buildAndFail()
        }

        then:
        def e = thrown InvalidRunnerConfigurationException
        e.cause instanceof UnsupportedVersionException
        e.cause.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support the plugin classpath injection feature used by GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    private String getMaxUnsupportedVersion(GradleVersion minSupportedVersion) {
        RELEASED_VERSION_DISTRIBUTIONS.getPrevious(minSupportedVersion).version.version
    }

    static String pluginDeclaration() {
        """
        plugins {
            id 'com.company.helloworld'
        }
        """
    }

    private void compilePluginProjectSources(File projectDir) {
        GFileUtils.mkdirs(projectDir)
        fixture.createPluginProjectSourceFiles(projectDir)
        new ForkingGradleExecuter(new UnderDevelopmentGradleDistribution(), testDirectoryProvider)
            .usingProjectDirectory(projectDir)
            .withArguments('classes', '--no-daemon')
            .run()
    }

}
