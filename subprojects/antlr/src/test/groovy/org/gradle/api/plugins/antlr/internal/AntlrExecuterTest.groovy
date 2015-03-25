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

package org.gradle.api.plugins.antlr.internal

import spock.lang.Specification

/**
 * Created by Rene on 25/03/15.
 */
class AntlrExecuterTest extends Specification {

    AntlrExecuter executer = new AntlrExecuter()

    def tracePropertiesAddedToArgumentList() {
        when:
        AntlrSpec spec = Mock()

        _ * spec.outputDirectory >> destFile()
        _ * spec.getArguments() >> []
        _ * spec.isTrace() >> true
        _ * spec.isTraceLexer() >> true
        _ * spec.isTraceParser() >> true
        _ * spec.isTraceTreeWalker() >> true

        then:
        executer.buildCommonArguments(spec).contains("-trace")
        executer.buildCommonArguments(spec).contains("-traceLexer")
        executer.buildCommonArguments(spec).contains("-traceParser")
        executer.buildCommonArguments(spec).contains("-traceTreeWalker")
    }

    def customTraceArgumentsOverrideProperties() {
        when:
        AntlrSpec spec = Mock()
        _ * spec.outputDirectory >> destFile()
        _ * spec.getArguments() >> ["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]

        then:
        executer.buildCommonArguments(spec).contains("-trace")
        executer.buildCommonArguments(spec).contains("-traceLexer")
        executer.buildCommonArguments(spec).contains("-traceParser")
        executer.buildCommonArguments(spec).contains("-traceTreeWalker")
    }

    def traceArgumentsDoNotDuplicateTrueTraceProperties() {
        when:
        AntlrSpec spec = Mock()
        _ * spec.outputDirectory >> destFile()
        _ * spec.getArguments() >> ["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]
        _ * spec.isTrace() >> true
        _ * spec.isTraceLexer() >> true
        _ * spec.isTraceParser() >> true
        _ * spec.isTraceTreeWalker() >> true

        then:
        executer.buildCommonArguments(spec).count {it == "-trace"} == 1
        executer.buildCommonArguments(spec).count {it == "-traceLexer"} == 1
        executer.buildCommonArguments(spec).count {it == "-traceParser"} == 1
        executer.buildCommonArguments(spec).count {it == "-traceTreeWalker"} == 1
    }

    def buildCommonArgumentsAddsAllParameters() {
        when:
        AntlrSpec spec = Mock()
        _ * spec.outputDirectory >> destFile()
        _ * spec.getArguments() >> ["-test"]
        _ * spec.isTrace() >> true
        _ * spec.isTraceLexer() >> true
        _ * spec.isTraceParser() >> true
        _ * spec.isTraceTreeWalker() >> true

        then:
        executer.buildCommonArguments(spec) == ["-o", "/output", "-test", "-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"]
    }

    def destFile() {
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"
        dest
    }
}
