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

package org.gradle.api.plugins.antlr.internal

import spock.lang.Specification

class AntlrExecuterTest extends Specification {
    AntlrExecuter exec

    def setup() {
        exec = new AntlrExecuter()
    }

    def processReturnsValidResultForToolv2() {
        given:
        def tool = Mock(antlr.Tool)
        String[] args = ["a", "b", "c"]

        when:
        def result = exec.process(tool, args)

        then:
        1 * tool.doEverything(_)
        result.getErrorCount() == 0
    }

    def processReturnsValidResultForToolv3() {
        given:
        def tool = Mock(org.antlr.Tool)
        tool.getNumErrors() >> 2

        when:
        def result = exec.process(tool)

        then:
        1 * tool.process()
        result.getErrorCount() == 2
    }

    def processReturnsValidResultForToolv4() {
        given:
        def tool = Mock(org.antlr.v4.Tool)
        tool.getNumErrors() >> 2

        when:
        def result = exec.process(tool)

        then:
        1 * tool.processGrammarsOnCommandLine()
        result.getErrorCount() == 2
    }

    def loadToolSucceeds() {
        given:
        String[] args = ["a", "b", "c"]

        when:
        def tool = exec.loadTool("org.antlr.Tool", args)

        then:
        tool instanceof org.antlr.Tool
    }

    def loadToolClassNotFound() {
        given:
        String[] args = ["a", "b", "c"]

        when:
        def tool = exec.loadTool("org.antlr.fake.Tool", args)

        then:
        thrown(ClassNotFoundException)
    }
}
