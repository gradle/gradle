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

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.configuration.InitScriptProcessor
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class InitScriptHandlerTest extends Specification {

    @Rule TestNameTestDirectoryProvider testDirectoryProvider

    final InitScriptProcessor processor = Mock()
    final GradleInternal gradle = Mock()
    final InitScriptHandler handler = new InitScriptHandler(processor)
    
    def "finds and processes init scripts"() {
        File script1 = testDirectoryProvider.createFile("script1.gradle")
        File script2 = testDirectoryProvider.createFile("script2.gradle")

        when:
        handler.executeScripts(gradle)
        
        then:
        _ * gradle.startParameter >> {
            Mock(StartParameter) {
                getAllInitScripts() >> {
                    [script1, script2]
                }
            }
        }

        1 * processor.process({ UriScriptSource source -> source.resource.file == script1 }, gradle)
        1 * processor.process({ UriScriptSource source -> source.resource.file == script2 }, gradle)
        0 * _._
    }
}
