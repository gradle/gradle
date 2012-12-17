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
import org.gradle.util.HelperUtil

class AntlrPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject()

    def addsAntlrPropertiesToEachSourceSet() {
        when:
        project.apply plugin: AntlrPlugin

        then:
        def main = project.sourceSets.main
        main.antlr.srcDirs == [project.file('src/main/antlr')] as Set

        def test = project.sourceSets.test
        test.antlr.srcDirs == [project.file('src/test/antlr')] as Set

        when:
        project.sourceSets.add('custom')

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
        project.sourceSets.add('custom')

        then:
        def custom = project.tasks.generateCustomGrammarSource
        custom instanceof AntlrTask
        project.tasks.compileCustomJava.taskDependencies.getDependencies(null).contains(custom)
    }
}
