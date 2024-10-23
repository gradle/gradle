/*
 * Copyright 2017 the original author or authors.
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

import org.apache.commons.io.FilenameUtils
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild", "nowInAndroidBuild"])
)
class RealLifeAndroidStudioPerformanceTest extends AbstractCrossVersionPerformanceTest implements AndroidPerformanceTestFixture {

    /**
     * To run this test locally you should have Android Studio installed in /Applications/Android Studio.*.app folder,
     * or you should set "studioHome" system property with the Android Studio installation path,
     * or you should enable automatic download of Android Studio with the -PautoDownloadAndroidStudio=true.
     *
     * Additionally, you should also have ANDROID_SDK_ROOT env. variable set with Android SDK (normally on MacOS it's installed in "$HOME/Library/Android/sdk").
     *
     * To enable headless mode run with -PrunAndroidStudioInHeadlessMode=true.
     */
    def "run Android Studio sync"() {
        given:
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        def testProject = AndroidTestProject.projectFor(runner.testProject)
        testProject.configure(runner)
        // TODO Restore AndroidTestProject.useAgpLatestStableOrRcVersion(runner)
        //      The project is using an incompatible version (AGP x.y.z) of the Android Gradle plugin.
        //      Latest supported version is AGP 8.4.0.
        //      Can't upgrade to Studio > Jellyfish with the current infra because no more ZIP available for mac.
        AndroidTestProject.configureForAgpVersion(runner, "8.4.0")
        AndroidTestProject.useKotlinLatestStableOrRcVersion(runner)
        runner.warmUpRuns = 20
        runner.runs = 20
        runner.setupAndroidStudioSync()
        configureLocalProperties()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    void configureLocalProperties() {
        assert System.getenv("ANDROID_SDK_ROOT") != null
        String androidSdkRootPath = System.getenv("ANDROID_SDK_ROOT")
        runner.addBuildMutator { invocation -> new LocalPropertiesMutator(invocation, FilenameUtils.separatorsToUnix(androidSdkRootPath)) }
    }

    static class LocalPropertiesMutator implements BuildMutator {
        private final String androidSdkRootPath
        private final InvocationSettings invocation

        LocalPropertiesMutator(InvocationSettings invocation, String androidSdkRootPath) {
            this.invocation = invocation
            this.androidSdkRootPath = androidSdkRootPath
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def localProperties = new File(invocation.projectDir, "local.properties")
            localProperties << "\nsdk.dir=$androidSdkRootPath\n"
        }
    }
}
