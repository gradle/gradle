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

package org.gradle.configurationcache.inputs.process.instrument

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Shared

/**
 * Base class for tests that invoke external process with the dynamic Groovy code.
 * There are many different ways to run a process in Groovy, and all are producing different byte code.
 * The class supports compiling Groovy code in both indy and old-school CallSite modes.
 */
abstract class AbstractProcessInstrumentationInDynamicGroovyIntegrationTest extends AbstractProcessInstrumentationIntegrationTest implements DynamicGroovyPluginMixin {
    @Shared
    def testCases = testCasesWithIndyModes()

    /**
     * Produces a list of test cases. Each test case is itself a list of the following structure:
     * {@code [varInitializer, processCreator, expectedPwdSuffix, expectedEnvVar]}.
     * @return the list of test cases
     */
    abstract def testCases()

    /**
     * Produces a list of indy compilation modes. Each test case from {@link #testCases()} will run in every mode from the returned list.
     * @return the list of indy modes
     */
    def indyModes() {
        return [true, false]
    }

    final def testCasesWithIndyModes() {
        // Combine each test case with enableIndy=true and/or enableIndy=false
        return [testCases(), indyModes()].combinations().collect { it.flatten() }
    }

    def "#title is intercepted in groovy build script #indyStatus"(VarInitializer varInitializer) {
        given:
        def cwd = testDirectory.file(expectedPwdSuffix)
        withPluginCode("""
                import org.codehaus.groovy.runtime.ProcessGroovyMethods
                import static org.codehaus.groovy.runtime.ProcessGroovyMethods.execute
            """, """
                ${varInitializer.getGroovy(baseScript.getRelativeCommandLine(cwd))}
                def process = $processCreator
                process.waitForProcessOutput(System.out, System.err)
            """, enableIndy)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("FOOBAR=$expectedEnvVar\nCWD=${cwd.path}")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin class 'SomePlugin': external process started")
        }

        where:
        [varInitializer, processCreator, expectedPwdSuffix, expectedEnvVar, enableIndy] << testCases

        title = processCreator.replace("command", varInitializer.description)
        indyStatus = enableIndy ? "with indy" : "without indy"
    }


    // Lift the visibility of the method to make it available for the mixin
    @Override
    TestFile buildScript(@GroovyBuildScriptLanguage String script) {
        super.buildScript(script)
    }
}
