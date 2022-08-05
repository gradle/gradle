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

import org.gradle.api.internal.file.TestFiles
import org.gradle.process.internal.DefaultExecSpec

class ProviderCompatibleExecSpecTest extends ProviderCompatibleBaseExecSpecTestBase {
    def "spec sets commandLine on parameters"() {
        given:
        def parameters = newParameters()
        def command = ["some", "command", "with", "arguments"]

        when:
        specUnderTest.commandLine = command
        specUnderTest.copyToParameters(parameters)

        then:
        parameters.commandLine.get() == command
    }

    @Override
    protected ProviderCompatibleExecSpec createSpecUnderTest() {
        return new ProviderCompatibleExecSpec(new DefaultExecSpec(TestFiles.pathToFileResolver(tmpDir.testDirectory)))
    }
}
