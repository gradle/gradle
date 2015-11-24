/*
 * Copyright 2014 the original author or authors.
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


package org.gradle.nativeplatform.test.googletest
import org.gradle.integtests.fixtures.Sample
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class GoogleTestSamplesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule public final Sample googleTest = sample(temporaryFolder, 'google-test')

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}", name)
    }

    def "googleTest"() {
        given:
        // GoogleTest sample only works out of the box with VS2013 on windows
        if (OperatingSystem.current().windows && !isVisualCpp2013()) {
            return
        }
        sample googleTest

        when:
        succeeds "runPassing"

        then:
        executedAndNotSkipped ":compileOperatorsTestPassingGoogleTestExeOperatorsTestCpp",
                ":linkPassingOperatorsTestGoogleTestExe", ":operatorsTestPassingGoogleTestExe",
                ":installPassingOperatorsTestGoogleTestExe", ":runPassingOperatorsTestGoogleTestExe"

        and:
        def passingResults = new GoogleTestTestResults(googleTest.dir.file("build/test-results/operatorsTestGoogleTestExe/passing/test_detail.xml"))
        passingResults.suiteNames == ['OperatorTests']
        passingResults.suites['OperatorTests'].passingTests == ['test_minus', 'test_plus']
        passingResults.suites['OperatorTests'].failingTests == []
        passingResults.checkTestCases(2, 2, 0)

        when:
        sample googleTest
        fails "runFailing"

        then:
        executedAndNotSkipped ":compileOperatorsTestFailingGoogleTestExeOperatorsTestCpp",
                ":linkFailingOperatorsTestGoogleTestExe", ":operatorsTestFailingGoogleTestExe",
                ":installFailingOperatorsTestGoogleTestExe", ":runFailingOperatorsTestGoogleTestExe"

        and:
        def failingResults = new GoogleTestTestResults(googleTest.dir.file("build/test-results/operatorsTestGoogleTestExe/failing/test_detail.xml"))
        failingResults.suiteNames == ['OperatorTests']
        failingResults.suites['OperatorTests'].passingTests == ['test_minus']
        failingResults.suites['OperatorTests'].failingTests == ['test_plus']
        failingResults.checkTestCases(2, 1, 1)
    }

    private static boolean isVisualCpp2013() {
        return (AbstractInstalledToolChainIntegrationSpec.toolChain.visualCpp && (AbstractInstalledToolChainIntegrationSpec.toolChain as AvailableToolChains.InstalledVisualCpp).version.major == "12")
    }
}
