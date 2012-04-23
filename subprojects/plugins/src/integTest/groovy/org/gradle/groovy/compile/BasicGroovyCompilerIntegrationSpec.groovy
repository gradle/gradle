/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.groovy.compile

import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.ExecutionFailure

@TargetVersions(['1.5.8', '1.6.9', '1.7.10', '1.8.6'])
abstract class BasicGroovyCompilerIntegrationSpec extends MultiVersionIntegrationSpec {
    @Rule TestResources resources = new TestResources()

    def setup() {
        executer.withArguments("-i")
    }

    def "badCodeBreaksBuild"() {
        when:
        runAndFail("classes")

        then:
        compileErrorOutput.contains 'unable to resolve class Unknown1'
        compileErrorOutput.contains 'unable to resolve class Unknown2'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "badJavaCodeBreaksBuild"() {
        when:
        runAndFail("classes")

        then:
        compileErrorOutput.contains 'illegal start of type'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "canCompileAgainstGroovyClassThatDependsOnExternalClass"() {
        when:
        run("test")

        then:
        noExceptionThrown()
    }

    @Override
    protected ExecutionResult run(String... tasks) {
        tweakBuildFile()
        return super.run(tasks)
    }

    @Override
    protected ExecutionFailure runAndFail(String... tasks) {
        tweakBuildFile()
        return super.runAndFail(tasks)
    }

    private void tweakBuildFile() {
        buildFile << """
dependencies { groovy 'org.codehaus.groovy:groovy:$version' }
"""
        buildFile << compilerConfiguration()

        println "->> USING BUILD FILE: ${buildFile.text}"
    }

    abstract String compilerConfiguration()

    String getCompilationFailureMessage() {
        return "Compilation failed; see the compiler error output for details."
    }

    String getCompileErrorOutput() {
        return errorOutput
    }
}
