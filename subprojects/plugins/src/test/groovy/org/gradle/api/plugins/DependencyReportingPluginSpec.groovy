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

package org.gradle.api.plugins

import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 9/5/12
 */
class DependencyReportingPluginSpec extends Specification {

    def project = new ProjectBuilder().build()

    def "adds task"() {
        when:
        project.apply(plugin: 'dependency-reporting')

        then:
        project.tasks.getByName("dependencyInsight")
    }

    def "preconfigures task for java project"() {
        given:
        project.apply plugin: 'dependency-reporting'
        project.apply plugin: 'java'

        when:
        DependencyInsightReportTask task = project.tasks.getByName("dependencyInsight")

        then:
        task.configuration.name == 'compile'
    }
}
