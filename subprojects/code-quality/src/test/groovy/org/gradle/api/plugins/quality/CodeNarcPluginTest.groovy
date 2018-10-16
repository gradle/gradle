/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class CodeNarcPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(CodeNarcPlugin)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "adds codenarc configuration"() {
        def config = project.configurations.findByName("codenarc")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The CodeNarc libraries to be used for this project.'
    }

    def "adds codenarc extension"() {
        expect:
        CodeNarcExtension codenarc = project.extensions.codenarc
        codenarc.config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
        codenarc.configFile == project.file("config/codenarc/codenarc.xml")
        codenarc.maxPriority1Violations == 0
        codenarc.maxPriority2Violations == 0
        codenarc.maxPriority3Violations == 0
        codenarc.reportFormat == "html"
        codenarc.reportsDir == project.file("build/reports/codenarc")
        codenarc.sourceSets == []
        !codenarc.ignoreFailures
    }

    def "configures any additional codenarc tasks"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath == project.configurations.codenarc
        task.config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
        task.configFile == project.file("config/codenarc/codenarc.xml")
        task.maxPriority1Violations == 0
        task.maxPriority2Violations == 0
        task.maxPriority3Violations == 0
        task.reports.enabled*.name == ["html"]
        task.reports.html.destination == project.file("build/reports/codenarc/custom.html")
        task.ignoreFailures == false
    }

    def "can customize additional tasks via extension"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)

        project.codenarc {
            config = project.resources.text.fromFile("codenarc-config")
            maxPriority1Violations = 10
            maxPriority2Violations = 50
            maxPriority3Violations = 200
            reportFormat = "xml"
            reportsDir = project.file("codenarc-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath == project.configurations.codenarc
        task.config.inputFiles.singleFile == project.file("codenarc-config")
        task.configFile == project.file("codenarc-config")
        task.maxPriority1Violations == 10
        task.maxPriority2Violations == 50
        task.maxPriority3Violations == 200
        task.reports.enabled*.name == ["xml"]
        task.reports.xml.destination == project.file("codenarc-reports/custom.xml")
        task.ignoreFailures == true
    }

    def "can customize task directly"() {
        CodeNarc task = project.tasks.create("codenarcCustom", CodeNarc)

        task.reports.xml {
            enabled = true
            destination = project.file("build/foo.xml")
        }

        expect:
        task.reports {
            assert enabled == [html, xml] as Set
            assert xml.destination == project.file("build/foo.xml")
        }
    }
}
