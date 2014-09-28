/*
 * Copyright 2010 the original author or authors.
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

import spock.lang.Specification
import org.gradle.api.Project
import org.gradle.util.TestUtil

class AntlrPluginTest extends Specification {
    private final Project project = TestUtil.createRootProject()

    def addsAntlrPropertiesToEachSourceSet() {
        when:
        project.apply plugin: AntlrPlugin

        then:
        def main = project.sourceSets.main
        main.antlr.srcDirs == [project.file('src/main/antlr')] as Set

        def test = project.sourceSets.test
        test.antlr.srcDirs == [project.file('src/test/antlr')] as Set

        when:
        project.sourceSets.create('custom')

        then:
        def custom = project.sourceSets.custom
        custom.antlr.srcDirs == [project.file('src/custom/antlr')] as Set
    }
    
    def addsTaskForEachSourceSet() {
        when:
        project.apply plugin: AntlrPlugin

        then:
        def main = project.tasks.generateGrammarSource
        main instanceof AntlrTask
        project.tasks.compileJava.taskDependencies.getDependencies(null).contains(main)

        def test = project.tasks.generateTestGrammarSource
        test instanceof AntlrTask
        project.tasks.compileTestJava.taskDependencies.getDependencies(null).contains(test)

        when:
        project.sourceSets.create('custom')

        then:
        def custom = project.tasks.generateCustomGrammarSource
        custom instanceof AntlrTask
        project.tasks.compileCustomJava.taskDependencies.getDependencies(null).contains(custom)
    }

    def traceDefaultProperties() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source

        then:
        main.isTrace() == false
        main.isTraceLexer() == false
        main.isTraceParser() == false
        main.isTraceTreeWalker() == false
        !main.buildArguments().contains("-trace")
        !main.buildArguments().contains("-traceLexer")
        !main.buildArguments().contains("-traceParser")
        !main.buildArguments().contains("-traceTreeWalker")   
    }

    def tracePropertiesAddedToArgumentList() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.isTrace() == true
        main.isTraceLexer() == true
        main.isTraceParser() == true
        main.isTraceTreeWalker() == true
        main.buildArguments().contains("-trace")
        main.buildArguments().contains("-traceLexer")
        main.buildArguments().contains("-traceParser")
        main.buildArguments().contains("-traceTreeWalker")   
    }

    def customArgumentsAdded() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source
        main.setArguments(["-a", "-b"])

        then:
        main.buildArguments().contains("-a")
        main.buildArguments().contains("-b")
    }

    def customTraceArgumentsOverrideProperties() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source
        main.setArguments(["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"])

        then:
        main.buildArguments().contains("-trace")
        main.buildArguments().contains("-traceLexer")
        main.buildArguments().contains("-traceParser")
        main.buildArguments().contains("-traceTreeWalker")
    }

    def traceArgumentsDoNotDuplicateTrueTraceProperties() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source
        main.setArguments(["-trace", "-traceLexer", "-traceParser", "-traceTreeWalker"])
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.buildArguments().count {it == "-trace"} == 1
        main.buildArguments().count {it == "-traceLexer"} == 1
        main.buildArguments().count {it == "-traceParser"} == 1
        main.buildArguments().count {it == "-traceTreeWalker"} == 1
    }

    def buildArgumentsAddsAllParameters() {
        given:
        File source = Mock()
        File s1 = Mock()
        File s2 = Mock()
        s1.getAbsolutePath() >> "/input/1"
        s2.getAbsolutePath() >> "/input/2"
        source.listFiles() >> [s1, s2]
        File dest = Mock()
        dest.getAbsolutePath() >> "/output"

        when:
        project.apply plugin: AntlrPlugin
        def main = project.tasks.generateGrammarSource
        main.outputDirectory = dest
        main.sourceDirectory = source
        main.setArguments(["-test"])
        main.setTrace(true)
        main.setTraceLexer(true)
        main.setTraceParser(true)
        main.setTraceTreeWalker(true)

        then:
        main.buildArguments() == ["-o", "/output", "-test", "-trace", "-traceLexer", "-traceParser", "-traceTreeWalker", "/input/1", "/input/2"]
    }
}
