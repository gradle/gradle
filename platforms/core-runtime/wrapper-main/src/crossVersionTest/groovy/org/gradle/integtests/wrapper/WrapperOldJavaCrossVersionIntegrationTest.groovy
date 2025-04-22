/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.wrapper

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.jvm.SupportedJavaVersionsExpectations
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@SuppressWarnings("IntegrationTestFixtures")
class WrapperOldJavaCrossVersionIntegrationTest extends AbstractWrapperCrossVersionIntegrationTest {

    @Requires(IntegTestPreconditions.UnsupportedWrapperJavaHomeAvailable)
    def 'provides reasonable failure message when attempting to run current Version with previous wrapper under java #jdk.javaVersion'() {
        when:
        GradleExecuter executor = prepareWrapperExecuter(previous, current).withJvm(jdk)

        then:
        def result = executor.withArgument('help').runWithFailure()
        result.assertHasErrorOutput(SupportedJavaVersionsExpectations.getErrorPattern(jdk.javaVersionMajor))

        where:
        jdk << AvailableJavaHomes.getUnsupportedWrapperJdks()
    }

    @Requires(IntegTestPreconditions.UnsupportedWrapperJavaHomeAvailable)
    def 'provides reasonable failure message when attempting to run with previous wrapper and the build is configured to use Java #jdk.javaVersion'() {
        when:
        GradleExecuter executor = prepareWrapperExecuter(previous, current)
        file("gradle.properties").writeProperties("org.gradle.java.home": jdk.javaHome.canonicalPath)

        then:
        def result = executor.withArgument('help').runWithFailure()
        result.assertHasErrorOutput(SupportedJavaVersionsExpectations.getErrorPattern(jdk.javaVersionMajor))

        where:
        jdk << AvailableJavaHomes.getUnsupportedWrapperJdks()
    }

}
