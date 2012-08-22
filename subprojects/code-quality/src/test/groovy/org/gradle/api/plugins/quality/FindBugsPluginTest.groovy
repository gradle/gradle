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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.util.HelperUtil

import spock.lang.Specification

import static org.gradle.util.Matchers.dependsOn
import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.that

class FindBugsPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(FindBugsPlugin)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "configures FindBugs configuration"() {
        def config = project.configurations.findByName("findbugs")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The FindBugs libraries to be used for this project.'
    }

    def "configures FindBugs extension"() {
        expect:
        FindBugsExtension extension = project.extensions.findbugs
        extension.reportsDir == project.file("build/reports/findbugs")
        !extension.ignoreFailures
        extension.effort == null
        extension.reportLevel == null
        extension.visitors == null
        extension.omitVisitors == null
        extension.includeFilter == null
        extension.excludeFilter == null
    }

    def "configures FindBugs task for each source set"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        configuresFindBugsTask("findbugsMain", project.sourceSets.main)
        configuresFindBugsTask("findbugsTest", project.sourceSets.test)
        configuresFindBugsTask("findbugsOther", project.sourceSets.other)
    }

    private void configuresFindBugsTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof FindBugs
        task.with {
            assert description == "Run FindBugs analysis for ${sourceSet.name} classes"
            assert source as List == sourceSet.allJava as List
            assert findbugsClasspath == project.configurations.findbugs
            assert classes.empty // no classes to analyze
            assert reports.xml.destination == project.file("build/reports/findbugs/${sourceSet.name}.xml")
            assert !ignoreFailures
            assert effort == null
            assert reportLevel == null
            assert visitors == null
            assert omitVisitors == null
            assert excludeFilter == null
            assert includeFilter == null
        }
    }

    def "configures any additional FindBugs tasks"() {
        def task = project.tasks.add("findbugsCustom", FindBugs)

        expect:
        task.description == null
        task.source.empty
        task.classes == null
        task.classpath == null
        task.findbugsClasspath == project.configurations.findbugs
        task.pluginClasspath == project.configurations.findbugsPlugins
        task.reports.xml.destination == project.file("build/reports/findbugs/custom.xml")
        !task.ignoreFailures
        task.effort == null
        task.reportLevel == null
        task.visitors == null
        task.omitVisitors == null
        task.excludeFilter == null
        task.includeFilter == null
    }

    def "adds FindBugs tasks to check lifecycle task"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.check, dependsOn(hasItems("findbugsMain", "findbugsTest", "findbugsOther")))
    }

    def "can customize settings via extension"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.findbugs {
            sourceSets = [project.sourceSets.main]
            reportsDir = project.file("findbugs-reports")
            ignoreFailures = true
            effort = 'min'
            reportLevel = 'high'
            visitors = ['org.gradle.Class']
            omitVisitors = ['org.gradle.Interface']
            includeFilter = new File("include.txt")
            excludeFilter = new File("exclude.txt")
        }

        expect:
        hasCustomizedSettings("findbugsMain", project.sourceSets.main)
        hasCustomizedSettings("findbugsTest", project.sourceSets.test)
        hasCustomizedSettings("findbugsOther", project.sourceSets.other)
        that(project.check, dependsOn(hasItem("findbugsMain")))
        that(project.check, dependsOn(not(hasItems("findbugsTest", "findbugsOther"))))
    }

    private void hasCustomizedSettings(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof FindBugs
        task.with {
            assert description == "Run FindBugs analysis for ${sourceSet.name} classes"
            assert source as List == sourceSet.allJava as List
            assert findbugsClasspath == project.configurations.findbugs
            assert reports.xml.destination == project.file("findbugs-reports/${sourceSet.name}.xml")
            assert ignoreFailures
            assert effort == 'min'
            assert reportLevel == 'high'
            assert visitors == ['org.gradle.Class']
            assert omitVisitors == ['org.gradle.Interface']
            assert includeFilter == new File("include.txt")
            assert excludeFilter == new File("exclude.txt")
        }
    }
    
    def "can customize any additional FindBugs tasks via extension"() {
        def task = project.tasks.add("findbugsCustom", FindBugs)
        project.findbugs {
            reportsDir = project.file("findbugs-reports")
            ignoreFailures = true
            effort = 'min'
            reportLevel = 'high'
            visitors = ['org.gradle.Class']
            omitVisitors = ['org.gradle.Interface']
            includeFilter = new File("include.txt")
            excludeFilter = new File("exclude.txt")
        }

        expect:
        task.description == null
        task.source.empty
        task.classes == null
        task.classpath == null
        task.findbugsClasspath == project.configurations.findbugs
        task.pluginClasspath == project.configurations.findbugsPlugins
        task.reports.xml.destination == project.file("findbugs-reports/custom.xml")
        task.ignoreFailures
        task.effort == 'min'
        task.reportLevel == 'high'
        task.visitors == ['org.gradle.Class']
        task.omitVisitors == ['org.gradle.Interface']
        task.includeFilter == new File("include.txt")
        task.excludeFilter == new File("exclude.txt")
    }

    def "can configure reporting"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
        }

        when:
        project.findbugsMain.reports {
            html {
                enabled true
            }
            xml.destination "foo"
        }

        then:
        noExceptionThrown()
    }
}
