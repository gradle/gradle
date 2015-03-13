/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.antlr

import org.gradle.api.Project
import org.gradle.util.TestUtil
import spock.lang.Specification

class AntlrTaskTest extends Specification {
    private final Project project = TestUtil.createRootProject()
    private AntlrTask main

    def setup() {
        main = project.tasks.create("generateGrammarSource", AntlrTask)
    }

    def traceDefaultProperties() {
        when:
        main.outputDirectory = destFile()
        then:
        main.isTrace() == false
        main.isTraceLexer() == false
        main.isTraceParser() == false
        main.isTraceTreeWalker() == false
        !main.buildCommonArguments().contains("-trace")
        !main.buildCommonArguments().contains("-traceLexer")
        !main.buildCommonArguments().contains("-traceParser")
        !main.buildCommonArguments().contains("-traceTreeWalker")
    }

    def tracePropertiesAddedToArgumentList() {
        when:
        main.outputDirectory = destFile()
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.isTrace() == true
        main.isTraceLexer() == true
        main.isTraceParser() == true
        main.isTraceTreeWalker() == true
        main.buildCommonArguments().contains("-trace")
        main.buildCommonArguments().contains("-traceLexer")
        main.buildCommonArguments().contains("-traceParser")
        main.buildCommonArguments().contains("-traceTreeWalker")
    }

    def customArgumentsAdded() {
        when:
        main.outputDirectory = destFile()
        main.setArguments(["-a", "-b"])

        then:
        main.buildCommonArguments().contains("-a")
        main.buildCommonArguments().contains("-b")
    }

    def customTraceArgumentsOverrideProperties() {
        when:
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = destFile()
        main.setArguments(["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"])

        then:
        main.buildCommonArguments().contains("-trace")
        main.buildCommonArguments().contains("-traceLexer")
        main.buildCommonArguments().contains("-traceParser")
        main.buildCommonArguments().contains("-traceTreeWalker")
    }

    def traceArgumentsDoNotDuplicateTrueTraceProperties() {
        when:
        main.outputDirectory = destFile()
        main.setArguments(["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"])
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.buildCommonArguments().count {it == "-trace"} == 1
        main.buildCommonArguments().count {it == "-traceLexer"} == 1
        main.buildCommonArguments().count {it == "-traceParser"} == 1
        main.buildCommonArguments().count {it == "-traceTreeWalker"} == 1
    }

    def buildCommonArgumentsAddsAllParameters() {
        when:
        main.outputDirectory = destFile()
        main.setArguments(["-test"])
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.buildCommonArguments() == ["-o", "/output", "-test", "-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]
    }

    def destFile() {
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"
        dest
    }
}
