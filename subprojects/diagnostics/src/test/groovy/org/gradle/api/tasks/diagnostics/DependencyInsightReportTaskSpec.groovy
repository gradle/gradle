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
        task.getConfiguration().set(conf)

        when:
        task.report()

        then:
        thrown(InvalidUserDataException)
    }

    def "fails if dependency to include is empty"() {
        when:
        def conf = project.configurations.create("foo")
        task.getConfiguration().set(conf)
        task.getDependencyNotation().set("")
        task.report()

        then:
        thrown(UnsupportedNotationException)
    }

    def "can set spec and configuration directly"() {
        when:
        def conf = project.configurations.create("foo")
        task.getConfiguration().set(conf)
        task.dependencySpec { true }
        then:
        task.getEffectiveDependencySpec().isPresent()
        task.configuration.get() == conf
    }

    def "can set spec and configuration via methods"() {
        when:
        project.configurations.create("foo")
        task.configurationName = 'foo'
        task.getDependencyNotation().set('bar')
        then:
        task.getEffectiveDependencySpec().isPresent()
        task.configuration.get().name == 'foo'
    }

    def "configuration could be specified by camelCase shortcut"() {
        given:
        project.configurations.create("confAlpha")
        def confB = project.configurations.create("confBravo")

        when:
        task.configurationName = 'coB'
        task.dependencySpec { true }

        then:
        task.configuration.get() == confB
    }

    def "ambiguous configuration selection by camelCase shortcut fails"() {
        given:
        project.configurations.create("confAlpha")
        project.configurations.create("confAlfa")

        when:
        task.configurationName = 'coA'
        task.dependencySpec { true }
        task.report()

        then:
        def exception = thrown(Exception)
        exception instanceof InvalidUserDataException
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
        task.configurationName = "confBravo"

        then:
        task.configuration.get() == project.configurations.confBravo
    }
}
