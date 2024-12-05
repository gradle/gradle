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

package org.gradle.api.internal.provider.sources.process

import org.gradle.process.BaseExecSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Consumer

abstract class ProviderCompatibleBaseExecSpecTestBase extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());

    ProviderCompatibleBaseExecSpec specUnderTest

    void setup() {
        specUnderTest = createSpecUnderTest()
    }

    def "setting an environment overwrites added variables"() {
        given:
        specUnderTest.environment("SOMEVAR", "someval")

        when:
        specUnderTest.environment = [OTHERVAR: "otherval"]

        then:
        specUnderTest.getEnvironment().get() == [OTHERVAR: "otherval"]
    }

    def "adding variables after setting environment is working"() {
        given:
        specUnderTest.environment = [SOMEVAR: "someval"]

        when:
        specUnderTest.environment(OTHERVAR: "otherval")
        specUnderTest.environment("ADDEDVAR", "addedval")

        then:
        specUnderTest.getEnvironment().get() == [OTHERVAR: "otherval", SOMEVAR: "someval", ADDEDVAR: "addedval"]
    }

    def "spec copies environment properties to parameters"() {
        given:
        def parameters = newParameters()

        when:
        specUnderTest.copyToParameters(parameters)

        then:
        parameters.environment.isPresent()
        specUnderTest.getEnvironment().get() == parameters.environment.get()
    }

    def "spec sets ignoreExitValue on parameters"(boolean ignoreExitValue) {
        given:
        def parameters = newParameters()

        when:
        specUnderTest.ignoreExitValue.set(ignoreExitValue)
        specUnderTest.copyToParameters(parameters)

        then:
        parameters.ignoreExitValue.get() == ignoreExitValue

        where:
        ignoreExitValue << [true, false]
    }

    def "setting input stream is forbidden"() {
        when:
        specUnderTest.standardInput.set(new ByteArrayInputStream())
        then:
        thrown UnsupportedOperationException
    }

    def "setting output stream is forbidden"() {
        when:
        specUnderTest.standardOutput.set(new ByteArrayOutputStream())
        then:
        thrown UnsupportedOperationException
    }

    def "setting error stream is forbidden"() {
        when:
        specUnderTest.errorOutput.set(new ByteArrayOutputStream())
        then:
        thrown UnsupportedOperationException
    }

    def "spec without working directory doesn't set it on parameters"() {
        given:
        def parameters = newParameters()

        when:
        specUnderTest.copyToParameters(parameters)

        then:
        !parameters.getWorkingDirectory().isPresent()
    }

    def "spec with working directory sets it on parameters"(Consumer<BaseExecSpec> configureAction) {
        given:
        def parameters = newParameters()

        when:
        configureAction.accept(specUnderTest)
        specUnderTest.copyToParameters(parameters)

        then:
        parameters.getWorkingDirectory().isPresent()
        parameters.getWorkingDirectory().get().asFile.absolutePath == tmpDir.file("foo/bar").absolutePath

        where:
        configureAction                                   | _
        configure { it.workingDir("foo/bar") }            | _
        configure { it.workingDir = new File("foo/bar") } | _
    }

    protected abstract ProviderCompatibleBaseExecSpec createSpecUnderTest()

    static ProcessOutputValueSource.Parameters newParameters() {
        return TestUtil.newInstance(ProcessOutputValueSource.Parameters.class)
    }

    private static Consumer<BaseExecSpec> configure(Consumer<BaseExecSpec> configuration) {
        return configuration
    }
}
