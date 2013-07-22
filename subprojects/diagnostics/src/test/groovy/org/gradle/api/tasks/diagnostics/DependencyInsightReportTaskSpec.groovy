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
import org.gradle.util.HelperUtil
import spock.lang.Specification

class DependencyInsightReportTaskSpec extends Specification {

    def project = HelperUtil.createRootProject()
    def task = HelperUtil.createTask(DependencyInsightReportTask, project)

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
        thrown(InvalidUserDataException)
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
}
