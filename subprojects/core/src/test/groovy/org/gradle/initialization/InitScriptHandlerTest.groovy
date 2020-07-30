/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization


import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.configuration.InitScriptProcessor
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class InitScriptHandlerTest extends Specification {

    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def processor = Mock(InitScriptProcessor)
    def executor = new TestBuildOperationExecutor()
    def gradle = Mock(GradleInternal)
    def startParameter = Stub(StartParameterInternal)
    def resourceLoader = Stub(TextFileResourceLoader)
    def handler = new InitScriptHandler(processor, executor, resourceLoader)

    def setup() {
        _ * gradle.startParameter >> startParameter
    }

    def "does nothing when there are no init scripts"() {
        given:
        startParameter.allInitScripts >> []

        when:
        handler.executeScripts(gradle)

        then:
        0 * executor._
        0 * processor._
    }

    def "finds and processes init scripts"() {
        File script1 = testDirectoryProvider.createFile("script1.gradle")
        File script2 = testDirectoryProvider.createFile("script2.gradle")

        given:
        startParameter.allInitScripts >> [script1, script2]

        when:
        handler.executeScripts(gradle)

        then:
        1 * processor.process({ TextResourceScriptSource source -> source.resource.file == script1 }, gradle)
        1 * processor.process({ TextResourceScriptSource source -> source.resource.file == script2 }, gradle)
        0 * executor._
        0 * processor._
    }
}
