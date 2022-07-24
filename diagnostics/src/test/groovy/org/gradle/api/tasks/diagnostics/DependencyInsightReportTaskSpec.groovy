/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.InvalidUserDataException
import org.gradle.api.specs.Spec
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class DependencyInsightReportTaskSpec extends AbstractProjectBuilderSpec {
    DependencyInsightReportTask task

    def setup() {
        task = TestUtil.createTask(DependencyInsightReportTask, project)
    }

    def "fails if configuration missing"() {
        when:
        task.report()

        then:
        thrown(InvalidUserDataException)
    }

    def "fails if dependency to include missing"() {
        def conf = project.configurations.create("foo")
        task.configuration = conf

        when:
        task.report()

        then:
        thrown(InvalidUserDataException)
    }

    def "fails if dependency to include is empty"() {
        when:
        task.setDependencySpec("")

        then:
        thrown(UnsupportedNotationException)
    }

    def "can set spec and configuration directly"() {
        when:
        def conf = project.configurations.create("foo")
        task.configuration = conf
        task.dependencySpec = { true } as Spec
        then:
        task.dependencySpec != null
        task.configuration == conf
    }

    def "can set spec and configuration via methods"() {
        when:
        project.configurations.create("foo")
        task.setConfiguration 'foo'
        task.setDependencySpec 'bar'
        then:
        task.dependencySpec != null
        task.configuration.name == 'foo'
    }

    def "can limit the paths to a dependency"() {
        when:
        project.configurations.create("foo")
        task.setConfiguration 'foo'
        task.setDependencySpec 'bar'
        task.setShowSinglePathToDependency true

        then:
        task.dependencySpec != null
        task.configuration.name == 'foo'
        task.showSinglePathToDependency == true
    }

    def "configuration could be specified by camelCase shortcut"() {
        given:
        project.configurations.create("confAlpha")
        def confB = project.configurations.create("confBravo")

        when:
        task.configuration = "coB"
        task.dependencySpec = { true } as Spec

        then:
        task.configuration == confB
    }

    def "ambiguous configuration selection by camelCase shortcut fails"() {
        given:
        project.configurations.create("confAlpha")
        project.configurations.create("confAlfa")

        when:
        task.configuration = "coA"
        task.dependencySpec = { true } as Spec

        then:
        thrown InvalidUserDataException
    }

    def "rule-defined configuration should be found"() {
        given:
        project.afterEvaluate { p ->
            p.configurations.addRule("configuration defined by a rule", { s ->
                if (s == "confBravo") {
                    p.configurations.create("confBravo")
                }
            })
        }

        when:
        project.evaluate()
        task.configuration = "confBravo"

        then:
        task.configuration == project.configurations.confBravo
    }
}
