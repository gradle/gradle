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

import org.gradle.api.Project
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.util.HelperUtil
import org.gradle.api.plugins.JavaBasePlugin

import spock.lang.Specification

import static org.gradle.util.Matchers.dependsOn
import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.that

class CheckstylePluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(CheckstylePlugin)
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
        extension.configProperties == [:]
        extension.reportsDir == project.file("build/reports/checkstyle")
        !extension.ignoreFailures
    }

    def "configures checkstyle task for each source set"() {
        project.plugins.apply(JavaBasePlugin)
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
            assert configURL == null
            assert configProperties == [:]
            assert resultFile == project.file("build/reports/checkstyle/${sourceSet.name}.xml")
            assert !ignoreFailures
            assert showViolations
        }
    }
    
    def "configures any additional checkstyle tasks"() {
        def task = project.tasks.add("checkstyleCustom", Checkstyle)

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath == project.configurations.checkstyle
        task.configFile == project.file("config/checkstyle/checkstyle.xml")
        task.configURL == null
        task.configProperties == [:]
        task.resultFile == project.file("build/reports/checkstyle/custom.xml")
        !task.ignoreFailures
    }

    def "adds checkstyle tasks to check lifecycle task"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }
        
        expect:
        that(project.tasks['check'], dependsOn(hasItems("checkstyleMain", "checkstyleTest", "checkstyleOther")))
    }
    
    def "can customize settings via extension"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }
        
        project.checkstyle {
            sourceSets = [project.sourceSets.main]
            configFile = project.file("checkstyle-config")
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
            displayViolations = true
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
            assert configURL == null
            assert configProperties == [foo: "foo"]
            assert resultFile == project.file("checkstyle-reports/${sourceSet.name}.xml")
            assert ignoreFailures
            assert showViolations
        }
    }
    
    def "can customize any additional checkstyle tasks via extension"() {
        def task = project.tasks.add("checkstyleCustom", Checkstyle)
        project.checkstyle {
            configFile = project.file("checkstyle-config")
            configURL = 'http://www.example.com/'
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath == project.configurations.checkstyle
        task.configFile == project.file("checkstyle-config")
        task.configURL == 'http://www.example.com/'
        task.configProperties == [foo: "foo"]
        task.resultFile == project.file("checkstyle-reports/custom.xml")
        task.ignoreFailures
    }
    
}
