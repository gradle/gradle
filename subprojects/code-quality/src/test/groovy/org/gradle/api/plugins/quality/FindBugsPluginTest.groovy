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

import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.*
import static spock.util.matcher.HamcrestSupport.that

class FindBugsPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(FindBugsPlugin)
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
        extension.includeFilterConfig == null
        extension.excludeFilterConfig == null
        extension.excludeBugsFilterConfig == null
        extension.includeFilter == null
        extension.excludeFilter == null
        extension.excludeBugsFilter == null
    }

    def "configures FindBugs task for each source set"() {
        project.pluginManager.apply(JavaBasePlugin)
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
        with(task) {
            description == "Run FindBugs analysis for ${sourceSet.name} classes"
            source as List == sourceSet.allJava as List
            findbugsClasspath == project.configurations.findbugs
            candidateClassFiles.empty // no classes to analyze
            reports.xml.destination == project.file("build/reports/findbugs/${sourceSet.name}.xml")
            !ignoreFailures
            effort == null
            reportLevel == null
            visitors == null
            omitVisitors == null
            excludeFilterConfig == null
            includeFilterConfig == null
            excludeBugsFilterConfig == null
            excludeFilter == null
            includeFilter == null
            excludeBugsFilter == null
            extraArgs == null
            jvmArgs == null
        }
    }

    def "configures any additional FindBugs tasks"() {
        def task = project.tasks.create("findbugsCustom", FindBugs)

        expect:
        with(task) {
            description == null
            source.empty
            classes == null
            classpath == null
            findbugsClasspath == project.configurations.findbugs
            pluginClasspath == project.configurations.findbugsPlugins
            reports.xml.destination == project.file("build/reports/findbugs/custom.xml")
            !ignoreFailures
            effort == null
            reportLevel == null
            visitors == null
            omitVisitors == null
            excludeFilterConfig == null
            includeFilterConfig == null
            excludeBugsFilterConfig == null
            excludeFilter == null
            includeFilter == null
            excludeBugsFilter == null
            extraArgs == null
            jvmArgs == null
        }
    }

    def "adds FindBugs tasks to check lifecycle task"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.check, dependsOn(hasItems("findbugsMain", "findbugsTest", "findbugsOther")))
    }

    def "can customize settings via extension"() {
        project.pluginManager.apply(JavaBasePlugin)
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
            excludeBugsFilter = new File("baselineBugs.txt")
            extraArgs = [ '-adjustPriority', 'DM_CONVERT_CASE=raise,DM_CONVERT_CASE=raise']
            jvmArgs = ['-Xdebug']
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
        with(task) {
            description == "Run FindBugs analysis for ${sourceSet.name} classes"
            source as List == sourceSet.allJava as List
            findbugsClasspath == project.configurations.findbugs
            reports.xml.destination == project.file("findbugs-reports/${sourceSet.name}.xml")
            ignoreFailures
            effort == 'min'
            reportLevel == 'high'
            visitors == ['org.gradle.Class']
            omitVisitors == ['org.gradle.Interface']
            includeFilterConfig.inputFiles.singleFile == project.file("include.txt")
            excludeFilterConfig.inputFiles.singleFile == project.file("exclude.txt")
            excludeBugsFilterConfig.inputFiles.singleFile == project.file("baselineBugs.txt")
            includeFilter == project.file("include.txt")
            excludeFilter == project.file("exclude.txt")
            excludeBugsFilter == project.file("baselineBugs.txt")
            extraArgs == [ '-adjustPriority', 'DM_CONVERT_CASE=raise,DM_CONVERT_CASE=raise' ]
            jvmArgs == ['-Xdebug']
        }
    }

    def "can customize any additional FindBugs tasks via extension"() {
        def task = project.tasks.create("findbugsCustom", FindBugs)
        project.findbugs {
            reportsDir = project.file("findbugs-reports")
            ignoreFailures = true
            effort = 'min'
            reportLevel = 'high'
            visitors = ['org.gradle.Class']
            omitVisitors = ['org.gradle.Interface']
            includeFilterConfig = project.resources.text.fromFile("include.txt")
            excludeFilterConfig = project.resources.text.fromFile("exclude.txt")
            excludeBugsFilterConfig = project.resources.text.fromFile("baselineBugs.txt")
            extraArgs = [ '-adjustPriority', 'DM_CONVERT_CASE=raise,DM_CONVERT_CASE=raise' ]
            jvmArgs = ['-Xdebug']
        }

        expect:
        with(task) {
            description == null
            source.empty
            classes == null
            classpath == null
            findbugsClasspath == project.configurations.findbugs
            pluginClasspath == project.configurations.findbugsPlugins
            reports.xml.destination == project.file("findbugs-reports/custom.xml")
            ignoreFailures
            effort == 'min'
            reportLevel == 'high'
            visitors == ['org.gradle.Class']
            omitVisitors == ['org.gradle.Interface']
            includeFilterConfig.inputFiles.singleFile == project.file("include.txt")
            excludeFilterConfig.inputFiles.singleFile == project.file("exclude.txt")
            excludeBugsFilterConfig.inputFiles.singleFile == project.file("baselineBugs.txt")
            includeFilter == project.file("include.txt")
            excludeFilter == project.file("exclude.txt")
            excludeBugsFilter == project.file("baselineBugs.txt")
            extraArgs == [ '-adjustPriority', 'DM_CONVERT_CASE=raise,DM_CONVERT_CASE=raise' ]
            jvmArgs == ['-Xdebug']
        }
    }

    def "can configure reporting"() {
        given:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
        }

        when:
        project.findbugsMain.reports {
            html {
                enabled true
            }
            xml.destination project.file("foo")
        }

        then:
        noExceptionThrown()
    }

    def "can use legacy includeFilter extension property"() {
        project.pluginManager.apply(JavaPlugin)

        project.findbugs.includeFilter = project.file("filter.txt")


        expect:
        project.findbugs.includeFilter == project.file("filter.txt")
        project.findbugs.includeFilterConfig.inputFiles.singleFile == project.file("filter.txt")
    }

    def "can use legacy excludeFilter extension property"() {
        project.pluginManager.apply(JavaPlugin)

        project.findbugs.excludeFilter = project.file("filter.txt")

        expect:
        project.findbugs.excludeFilter == project.file("filter.txt")
        project.findbugs.excludeFilterConfig.inputFiles.singleFile == project.file("filter.txt")
    }

    def "can use legacy excludeBugsFilter extension property"() {
        project.pluginManager.apply(JavaPlugin)

        project.findbugs.excludeBugsFilter = project.file("filter.txt")

        expect:
        project.findbugs.excludeBugsFilter == project.file("filter.txt")
        project.findbugs.excludeBugsFilterConfig.inputFiles.singleFile == project.file("filter.txt")
    }
}
