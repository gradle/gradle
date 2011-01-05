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
    private final AntlrPlugin plugin = new AntlrPlugin()

    def addsAntlrPropertiesToEachSourceSet() {
        when:
        plugin.apply(project)

        then:
        def sourceSet = project.sourceSets.main
        sourceSet.antlr.srcDirs == [project.file('src/main/antlr')] as Set

        sourceSet = project.sourceSets.test
        sourceSet.antlr.srcDirs == [project.file('src/test/antlr')] as Set

        when:
        project.sourceSets.add('custom')

        then:
        sourceSet = project.sourceSets.custom
        sourceSet.antlr.srcDirs == [project.file('src/custom/antlr')] as Set
    }
    
    def addsTaskForEachSourceSet() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks.generateGrammarSource
        task instanceof AntlrTask
        project.tasks.compileJava.taskDependencies.getDependencies(null).contains(task)

        task = project.tasks.generateTestGrammarSource
        task instanceof AntlrTask
        project.tasks.compileTestJava.taskDependencies.getDependencies(null).contains(task)

        when:
        project.sourceSets.add('custom')

        then:
        task = project.tasks.generateCustomGrammarSource
        task instanceof AntlrTask
        project.tasks.compileCustomJava.taskDependencies.getDependencies(null).contains(task)
    }
}
