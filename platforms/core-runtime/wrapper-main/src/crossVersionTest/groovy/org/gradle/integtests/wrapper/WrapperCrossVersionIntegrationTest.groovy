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


import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions


@SuppressWarnings("IntegrationTestFixtures")
class WrapperCrossVersionIntegrationTest extends AbstractWrapperCrossVersionIntegrationTest {
    @Requires(value = [
        OsTestPreconditions.NotWindowsJavaBefore11
    ], reason = "see https://github.com/gradle/gradle-private/issues/3758")
    void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(previous, current)

        then:
        checkWrapperWorksWith(executer, current)

        cleanup:
        cleanupDaemons(executer, current)
    }

    @Requires(value = [
        OsTestPreconditions.NotWindowsJavaBefore11
    ], reason = "see https://github.com/gradle/gradle-private/issues/3758")
    void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(current, previous).withWarningMode(null)

        then:
        checkWrapperWorksWith(executer, previous)

        cleanup:
        cleanupDaemons(executer, previous)
    }
}
