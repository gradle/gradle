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

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.that

class CheckstylePluginTest extends Specification {
    DefaultProject project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(CheckstylePlugin)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "configures checkstyle configuration"() {
        def config = project.configurations.findByName("checkstyle")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The Checkstyle libraries to be used for this project.'
    }

    def "configures checkstyle extension"() {
        expect:
        CheckstyleExtension extension = project.extensions.checkstyle
        extension.configFile == project.file("config/checkstyle/checkstyle.xml")
        extension.config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
        extension.configProperties == [:]
        extension.reportsDir == project.file("build/reports/checkstyle")
        !extension.ignoreFailures
    }

    def "configures checkstyle task for each source set"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        configuresCheckstyleTask("checkstyleMain", project.sourceSets.main)
        configuresCheckstyleTask("checkstyleTest", project.sourceSets.test)
        configuresCheckstyleTask("checkstyleOther", project.sourceSets.other)
    }

    private void configuresCheckstyleTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Checkstyle
        task.with {
            assert description == "Run Checkstyle analysis for ${sourceSet.name} classes"
            assert checkstyleClasspath == project.configurations["checkstyle"]
            assert classpath == sourceSet.output
            assert configFile == project.file("config/checkstyle/checkstyle.xml")
            assert config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
            assert configProperties == [:]
            assert reports.xml.destination == project.file("build/reports/checkstyle/${sourceSet.name}.xml")
            assert !ignoreFailures
            assert showViolations
        }
    }

    def "configures any additional checkstyle tasks"() {
        def task = project.tasks.create("checkstyleCustom", Checkstyle)

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath == project.configurations.checkstyle
        task.configFile == project.file("config/checkstyle/checkstyle.xml")
        task.config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
        task.configProperties == [:]
        task.reports.xml.destination == project.file("build/reports/checkstyle/custom.xml")
        !task.ignoreFailures
    }

    def "adds checkstyle tasks to check lifecycle task"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.tasks['check'], dependsOn(hasItems("checkstyleMain", "checkstyleTest", "checkstyleOther")))
    }

    def "can customize settings via extension"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.checkstyle {
            sourceSets = [project.sourceSets.main]
            config = project.resources.text.fromFile("checkstyle-config")
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
            showViolations = true
        }

        expect:
        hasCustomizedSettings("checkstyleMain", project.sourceSets.main)
        hasCustomizedSettings("checkstyleTest", project.sourceSets.test)
        hasCustomizedSettings("checkstyleOther", project.sourceSets.other)
        that(project.check, dependsOn(hasItem('checkstyleMain')))
        that(project.check, dependsOn(not(hasItems('checkstyleTest', 'checkstyleOther'))))
    }

    private void hasCustomizedSettings(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Checkstyle
        task.with {
            assert description == "Run Checkstyle analysis for ${sourceSet.name} classes"
            assert source as List == sourceSet.allJava as List
            assert checkstyleClasspath == project.configurations["checkstyle"]
            assert configFile == project.file("checkstyle-config")
            assert config.inputFiles.singleFile == project.file("checkstyle-config")
            assert configProperties == [foo: "foo"]
            assert reports.xml.destination == project.file("checkstyle-reports/${sourceSet.name}.xml")
            assert ignoreFailures
            assert showViolations
        }
    }

    def "can customize any additional checkstyle tasks via extension"() {
        def task = project.tasks.create("checkstyleCustom", Checkstyle)
        project.checkstyle {
            config = project.resources.text.fromFile("checkstyle-config")
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath == project.configurations.checkstyle
        task.configFile == project.file("checkstyle-config")
        task.config.inputFiles.singleFile == project.file("checkstyle-config")
        task.configProperties == [foo: "foo"]
        task.reports.xml.destination == project.file("checkstyle-reports/custom.xml")
        task.ignoreFailures
    }

    def "can use legacy configFile extension property"() {
        project.pluginManager.apply(JavaPlugin)

        project.checkstyle {
            configFile = project.file("checkstyle-config")
        }

        expect:
        project.checkstyle.configFile == project.file("checkstyle-config") // computed property
        project.tasks.checkstyleMain.configFile == project.file("checkstyle-config")
        project.tasks.checkstyleTest.configFile == project.file("checkstyle-config")
    }
}
