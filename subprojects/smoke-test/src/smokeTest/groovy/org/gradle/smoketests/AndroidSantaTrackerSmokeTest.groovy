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

import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.profiler.Phase
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS


@Requires(TestPrecondition.JDK11_OR_EARLIER)
class AndroidSantaTrackerSmokeTest extends AbstractAndroidSantaTrackerSmokeTest {

    @Unroll
    @UnsupportedWithInstantExecution(iterationMatchers = [AGP_3_ITERATION_MATCHER, AGP_4_0_ITERATION_MATCHER])
    def "check deprecation warnings produced by building Santa Tracker Java (agp=#agpVersion)"() {

        given:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir, 'Java', agpVersion)

        when:
        def result = buildLocation(checkoutDir, agpVersion)

        then:
        if (agpVersion.startsWith('3.6')) {
            expectDeprecationWarnings(result,
                "Internal API constructor DefaultDomainObjectSet(Class<T>) has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
                    "Please use ObjectFactory.domainObjectSet(Class<T>) instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_gradle_types.html#domainobjectset for more details."
            )
        } else {
            expectNoDeprecationWarnings(result)
        }
        assertInstantExecutionStateStored()

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }

    @Unroll
    @UnsupportedWithInstantExecution(iterationMatchers = [AGP_3_ITERATION_MATCHER, AGP_4_0_ITERATION_MATCHER])
    def "incremental Java compilation works for Santa Tracker Java (agp=#agpVersion)"() {

        given:
        def checkoutDir = temporaryFolder.createDir("checkout")
        setupCopyOfSantaTracker(checkoutDir, 'Java', agpVersion)
        def buildContext = new ScenarioContext(UUID.randomUUID(), "nonAbiChange").withBuild(Phase.MEASURE, 0)

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
        assertInstantExecutionStateStored()

        when:
        nonAbiChangeMutator.beforeBuild(buildContext)
        buildLocation(checkoutDir, agpVersion)
        def md5After = compiledClassFile.md5Hash

        then:
        result.task(":santa-tracker:compileDevelopmentDebugJavaWithJavac").outcome == SUCCESS
        assertInstantExecutionStateLoaded()
        md5After != md5Before

        where:
        agpVersion << TESTED_AGP_VERSIONS
    }
}
