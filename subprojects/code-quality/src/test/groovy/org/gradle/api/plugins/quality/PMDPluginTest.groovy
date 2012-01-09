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

import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.util.HelperUtil
import org.gradle.api.tasks.SourceSet

import spock.lang.Specification

import static spock.util.matcher.HamcrestSupport.that

class PmdPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(PmdPlugin)
    }

    def "applies java-base plugin"() {
        expect:
        project.plugins.hasPlugin(JavaBasePlugin)
    }

    def "configures pmd configuration"() {
        def config = project.configurations.findByName("pmd")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The PMD libraries to be used for this project.'
    }

    def "configures pmd extension"() {
        expect:
        PmdExtension extension = project.extensions.pmd
        extension.ruleSets == ["basic"]
        extension.ruleSetFiles.empty
        extension.xmlReportsDir == project.file("build/reports/pmd")
        extension.htmlReportsDir == project.file("build/reports/pmd")
        extension.ignoreFailures == false
    }

    def "configures pmd task for each source set"() {
        project.sourceSets {
            main
            test
            other
        }

        expect:
        configuresPmdTask("pmdMain", project.sourceSets.main)
        configuresPmdTask("pmdTest", project.sourceSets.test)
        configuresPmdTask("pmdOther", project.sourceSets.other)
    }

    private void configuresPmdTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Pmd
        task.with {
            assert description == "Run PMD analysis for ${sourceSet.name} classes"
            defaultSource == sourceSet.allJava
            assert pmdClassPath == project.configurations.pmd
            assert ruleSets == ["basic"]
            assert ruleSetFiles.empty
            assert xmlReportFile == project.file("build/reports/pmd/${sourceSet.name}.xml")
            assert htmlReportFile == project.file("build/reports/pmd/${sourceSet.name}.html")
            assert ignoreFailures == false
        }
    }

    def "adds pmd tasks to check lifecycle task"() {
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.tasks['check'], dependsOn(hasItems("pmdMain", "pmdTest", "pmdOther")))
    }

    def "can customize settings via extension"() {
        project.sourceSets {
            main
            test
            other
        }

        project.pmd {
            sourceSets = [project.sourceSets.main]
            ruleSets = ["braces", "unusedcode"]
            ruleSetFiles = project.files("my-ruleset.xml")
            xmlReportsDir = project.file("pmd-xml-reports")
            htmlReportsDir = project.file("pmd-html-reports")
            ignoreFailures = true
        }

        expect:
        hasCustomizedSettings("pmdMain", project.sourceSets.main)
        hasCustomizedSettings("pmdTest", project.sourceSets.test)
        hasCustomizedSettings("pmdOther", project.sourceSets.other)
        that(project.check, dependsOn(hasItem('pmdMain')))
        that(project.check, dependsOn(not(hasItems('pmdTest', 'pmdOther'))))
    }

    private void hasCustomizedSettings(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Pmd
        task.with {
            assert description == "Run PMD analysis for ${sourceSet.name} classes"
            defaultSource == sourceSet.allJava
            assert pmdClassPath == project.configurations.pmd
            assert ruleSets == ["braces", "unusedcode"]
            assert ruleSetFiles.files == project.files("my-ruleset.xml").files
            assert xmlReportFile == project.file("pmd-xml-reports/${sourceSet.name}.xml")
            assert htmlReportFile == project.file("pmd-html-reports/${sourceSet.name}.html")
            assert ignoreFailures == true
        }
    }
}
