/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class GroovyBasePluginTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(GroovyBasePluginTest)

    private ProjectInternal project

    def setup() {
        project = TestUtil.create(temporaryFolder).rootProject()
        project.version = "1.0"
        project.pluginManager.apply(GroovyBasePlugin)
    }

    def "applies the java base plugin to the project"() {
        expect:
        project.plugins.hasPlugin(JavaBasePlugin)
    }

    def "applies mappings to new source set"() {
        when:
        def sourceSet = project.sourceSets.create('custom')

        then:
        sourceSet.groovy.displayName == "custom Groovy source"
        sourceSet.groovy.srcDirs == toLinkedSet(project.file("src/custom/groovy"))
    }

    def "adds compile task to new source set"() {
        when:
        project.sourceSets.create('custom')
        def task = project.tasks['compileCustomGroovy']

        then:
        task instanceof GroovyCompile
        task.description == 'Compiles the custom Groovy source.'
        task dependsOn('compileCustomJava')
    }

    def "dependencies of java plugin tasks include groovy compile tasks"() {
        when:
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']

        then:
        assertThat(task, dependsOn(hasItem('compileCustomGroovy')))
    }

    def "configures additional tasks defined by the build script"() {
        when:
        def task = project.task('otherGroovydoc', type: Groovydoc)

        then:
        task.destinationDir == project.java.docsDir.file('groovydoc').get().asFile
        task.docTitle == "test-project 1.0 API"
        task.windowTitle == "test-project 1.0 API"
    }
}
