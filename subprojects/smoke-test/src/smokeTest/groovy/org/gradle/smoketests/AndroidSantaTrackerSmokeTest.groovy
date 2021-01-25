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

package org.gradle.smoketests


import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.profiler.DefaultScenarioContext
import org.gradle.profiler.Phase
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AndroidSantaTrackerSmokeTest extends AbstractAndroidSantaTrackerSmokeTest {

    // TODO:configuration-cache remove once fixed upstream
    @Override
    protected int maxConfigurationCacheProblems() {
        return 150
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_3_ITERATION_MATCHER, AGP_4_0_ITERATION_MATCHER])
    def "check deprecation warnings produced by building Santa Tracker Java (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir, 'Java', agpVersion)

        when:
        def result = buildLocation(checkoutDir, agpVersion)

        then:
        if (agpVersion.startsWith("3.6")) {
            expectDeprecationWarnings(
                    result,
                    "Internal API constructor DefaultDomainObjectSet(Class<T>) has been deprecated. " +
                            "This is scheduled to be removed in Gradle 7.0. Please use ObjectFactory.domainObjectSet(Class<T>) instead. " +
                            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_gradle_types.html#domainobjectset for more details.",
                    "The WorkerExecutor.submit() method has been deprecated. " +
                            "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
            )
        } else if (agpVersion.startsWith('4.0.2')) {
            expectDeprecationWarnings(
                    result,
                    "The WorkerExecutor.submit() method has been deprecated. " +
                            "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
            )
        } else if (agpVersion.startsWith('4.1')) {
            expectDeprecationWarnings(result, "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details.")
        } else {
            expectNoDeprecationWarnings(result)
        }
        assertConfigurationCacheStateStored()

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = [AGP_3_ITERATION_MATCHER, AGP_4_0_ITERATION_MATCHER])
    def "incremental Java compilation works for Santa Tracker Java (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir, 'Java', agpVersion)
        def buildContext = new DefaultScenarioContext(UUID.randomUUID(), "nonAbiChange").withBuild(Phase.MEASURE, 0)

        and:
        def pathToClass = "com/google/android/apps/santatracker/map/BottomSheetBehavior"
        def fileToChange = checkoutDir.file("santa-tracker/src/main/java/${pathToClass}.java")
        def compiledClassFile = checkoutDir.file("santa-tracker/build/intermediates/javac/developmentDebug/classes/${pathToClass}.class")
        def nonAbiChangeMutator = new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)

        when:
        def result = buildLocation(checkoutDir, agpVersion)
        def md5Before = compiledClassFile.md5Hash

        then:
        result.task(":santa-tracker:compileDevelopmentDebugJavaWithJavac").outcome == SUCCESS
        assertConfigurationCacheStateStored()

        when:
        nonAbiChangeMutator.beforeBuild(buildContext)
        buildLocation(checkoutDir, agpVersion)
        def md5After = compiledClassFile.md5Hash

        then:
        result.task(":santa-tracker:compileDevelopmentDebugJavaWithJavac").outcome == SUCCESS
        assertConfigurationCacheStateLoaded()
        md5After != md5Before

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = AGP_4_2_ITERATION_MATCHER)
    def "can lint Santa-Tracker #flavour (agp=#agpVersion)"() {

        given:
        AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(agpVersion)

        and:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir, flavour, agpVersion)

        when:
        def result = runnerForLocation(checkoutDir, agpVersion, "lintDebug").buildAndFail()

        then:
        assertConfigurationCacheStateStored()
        result.output.contains("Lint found errors in the project; aborting build.")

        when:
        runnerForLocation(checkoutDir, agpVersion, "clean").build()
        result = runnerForLocation(checkoutDir, agpVersion, "lintDebug").buildAndFail()

        then:
        assertConfigurationCacheStateLoaded()
        result.output.contains("Lint found errors in the project; aborting build.")

        where:
        [agpVersion, flavour] << [AGP_VERSIONS.getLatestsFromMinorPlusNightly("4.2"), ['Java', 'Kotlin']].combinations()
    }
}
