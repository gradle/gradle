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

package org.gradle.performance.regression.android

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.performance.regression.corefeature.AbstractIncrementalExecutionPerformanceTest
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.test.fixtures.file.LeaksFileHandles

import java.util.jar.JarOutputStream

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.OperatingSystem.MAC_OS
import static org.gradle.performance.results.OperatingSystem.WINDOWS

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX, WINDOWS, MAC_OS], testProjects = ["santaTrackerAndroidBuild", "nowInAndroidBuild"])
)
@LeaksFileHandles("The TAPI keeps handles to the distribution it starts open in the test JVM")
class AndroidIncrementalExecutionPerformanceTest extends AbstractIncrementalExecutionPerformanceTest implements AndroidPerformanceTestFixture {
    IncrementalAndroidTestProject testProject

    def setup() {
        testProject = AndroidTestProject.findProjectFor(runner.testProject) as IncrementalAndroidTestProject
        AndroidTestProject.useAgpLatestStableOrRcVersion(runner)
        AndroidTestProject.useKotlinLatestStableOrRcVersion(runner)
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.args.addAll(["--no-build-cache", "--no-scan"])
        // use the deprecated property so it works with previous versions
        runner.args.add("-D${StartParameterBuildOptions.ConfigurationCacheProblemsOption.DEPRECATED_PROPERTY_NAME}=warn")
        runner.warmUpRuns = 20
        applyEnterprisePlugin()
    }

    def "abi change#configurationCaching"() {
        given:
        if (configurationCachingEnabled && IncrementalAndroidTestProject.SANTA_TRACKER == testProject) {
            runner.addBuildMutator { settings ->
                new BuildMutator() {
                    @Override
                    void beforeBuild(BuildContext context) {
                        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.workingDir)
                    }
                }
            }
        }
        testProject.configureForAbiChange(runner)
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    def "non-abi change#configurationCaching"() {
        given:
        if (configurationCachingEnabled && IncrementalAndroidTestProject.SANTA_TRACKER == testProject) {
            runner.addBuildMutator { settings ->
                new BuildMutator() {
                    @Override
                    void beforeBuild(BuildContext context) {
                        SantaTrackerConfigurationCacheWorkaround.beforeBuild(runner.workingDir)
                    }
                }
            }
        }
        testProject.configureForNonAbiChange(runner)
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }

    @RunFor([])
    def "up-to-date assembleDebug#configurationCaching"() {
        given:
        runner.tasksToRun = [testProject.taskToRunForChange]
        enableConfigurationCaching(configurationCachingEnabled)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        configurationCachingEnabled << [true, false]
        configurationCaching = configurationCachingMessage(configurationCachingEnabled)
    }
}

class SantaTrackerConfigurationCacheWorkaround {
    static void beforeBuild(File workingDir) {
        workingDir.listFiles().findAll { new File(it, "settings.gradle").exists() }.forEach { projectDir ->
            // Workaround for Android Gradle plugin checking for the presence of these directories at configuration time,
            // which invalidates configuration cache if their presence changes. Create these directories before the first build.
            // See: https://android.googlesource.com/platform/tools/base/+/studio-master-dev/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/ShaderCompile.java#120
            // TODO: remove this once AGP stops checking for the existence of these directories at configuration time
            workingDir.listFiles().findAll { it.isDirectory() && new File(it, "build.gradle").exists() }.each {
                new File(it, "build/intermediates/merged_shaders/debug/out").mkdirs()
                new File(it, "build/intermediates/merged_shaders/debugUnitTest/out").mkdirs()
                new File(it, "build/intermediates/merged_shaders/debugAndroidTest/out").mkdirs()
                new File(it, "build/intermediates/merged_shaders/release/out").mkdirs()
                new File(it, "build/intermediates/merged_shaders/releaseAndroidTest/out").mkdirs()
            }
        }
        File androidAnalyticsSetting = new File(System.getProperty("user.home"), ".android/analytics.settings")
        if (!androidAnalyticsSetting.exists()) {
            androidAnalyticsSetting.parentFile.mkdirs()
            androidAnalyticsSetting.createNewFile()
        }
        workingDir.listFiles().findAll { it.name.contains("gradleUserHome") }.forEach { gradleUserHome ->
            def androidFakeDependency = new File(gradleUserHome, "android/FakeDependency.jar")
            if (!androidFakeDependency.exists()) {
                androidFakeDependency.parentFile.mkdirs()
                new JarOutputStream(new FileOutputStream(androidFakeDependency)).close()
            }
        }
    }
}
