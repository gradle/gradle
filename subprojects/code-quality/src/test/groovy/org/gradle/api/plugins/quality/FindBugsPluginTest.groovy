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
import org.gradle.api.InvalidUserDataException
import spock.lang.Specification
import static org.gradle.util.Matchers.dependsOn
import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.that

class FindBugsPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    // We consistently need files that actually exist
    File includeFile
    File excludeFile

    def setup() {
        project.plugins.apply(FindBugsPlugin)
        includeFile = File.createTempFile("include", "txt")
        excludeFile = File.createTempFile("exclude", "txt")
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "configures findbugs configuration"() {
        def config = project.configurations.findByName("findbugs")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The FindBugs libraries to be used for this project.'
    }

    def "configures findbugs extension"() {
        expect:
        FindBugsExtension extension = project.extensions.findbugs
        extension.reportsDir == project.file("build/reports/findbugs")
        !extension.ignoreFailures
    }

    def "configures findbugs task for each source set"() {
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
            assert ignoreFailures == false
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
        task.ignoreFailures == false
        task.effort == null
        task.reportLevel == null
        task.visitors == null
        task.omitVisitors == null
        task.excludeFilter == null
        task.includeFilter == null
    }

    def "adds findbugs tasks to check lifecycle task"() {
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
            excludeFilter = excludeFile
            includeFilter = includeFile
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
            assert ignoreFailures == true
            assert effort == 'min'
            assert reportLevel == 'high'
            assert visitors == ['org.gradle.Class']
            assert omitVisitors == ['org.gradle.Interface']
            assert excludeFilter == excludeFile
            assert includeFilter == includeFile
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
            excludeFilter = excludeFile
            includeFilter = includeFile
        }

        expect:
        task.description == null
        task.source.empty
        task.classes == null
        task.classpath == null
        task.findbugsClasspath == project.configurations.findbugs
        task.pluginClasspath == project.configurations.findbugsPlugins
        task.reports.xml.destination == project.file("findbugs-reports/custom.xml")
        task.ignoreFailures == true
        task.effort == 'min'
        task.reportLevel == 'high'
        task.visitors == ['org.gradle.Class']
        task.omitVisitors == ['org.gradle.Interface']
        task.excludeFilter == excludeFile
        task.includeFilter == includeFile
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
        notThrown()
    }
    

    private FindBugs setupWithMain() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
        }

        // Fake classes input
        project.findbugsMain.classes = project.files(".")

        project.findbugsMain
    }

    def "can generate spec"() {
        def task = setupWithMain()

        when:
        task.generateSpec()

        then:
        notThrown()
    }

    def "can configure optional arguments"() {
        def task = setupWithMain()

        task.effort = 'min'
        task.reportLevel = 'high'
        task.visitors = ['Check1','Check2'] as Set
        task.omitVisitors = ['Check3','Check4'] as Set

        expect:
        hasArgument(task, '-effort:min')
        hasArgument(task, '-high')
        hasArgument(task, '-visitors') && hasArgument(task,'Check1,Check2') 
    }

    private boolean hasArgument(task, Closure closure) {
        return task.generateSpec().getArguments().any(closure)
    }

    private boolean hasArgument(task, String contains) {
        return hasArgument(task) { it.contains(contains) }
    }

    def "can configure effort optional parameter"() {
        def task = setupWithMain()

        expect: !hasArgument(task,'-effort')

        when: task.effort = 'min'
        then: hasArgument(task, '-effort:min')

        when: task.effort = 'default'
        then: hasArgument(task, '-effort:default') 

        when: task.effort = 'max'
        then: hasArgument(task, '-effort:max')

        when:
            task.effort = 'invalid'
            task.generateSpec()
        then:
            thrown(InvalidUserDataException)
    }

    def "can configure reportLevel optional parameter"() {
        def task = setupWithMain()

        //expect: !hasArgument(task.optionalArguments().containsKey('reportLevel')

        when: task.reportLevel = 'experimental'
        then: hasArgument(task, '-experimental')

        when: task.reportLevel = 'low'
        then: hasArgument(task, '-low')

        when: task.reportLevel = 'medium'
        then: hasArgument(task, '-medium')

        when: task.reportLevel = 'high'
        then: hasArgument(task, '-high')

        when:
            task.reportLevel = 'invalid'
            task.generateSpec()
        then:
            thrown(InvalidUserDataException)
    }

    def "can configure visitor optional parameters"(String paramName) {
        def task = setupWithMain()

        expect: !hasArgument(task, "-${paramName}")

        when: task[paramName] = []
        then: !hasArgument(task, "-${paramName}")

        when: task[paramName] = ['Check1']
        then: 
            hasArgument(task, "-${paramName}") 
            hasArgument(task, 'Check1')

        when: task[paramName] = ['Check1','Check2']
        then:
            hasArgument(task, "-${paramName}")
            hasArgument(task, 'Check1,Check2')

        when: task[paramName] = ['Check1,Check2', 'Check3']
        then:
            hasArgument(task, "-${paramName}")
            hasArgument(task, 'Check1,Check2,Check3')

        where: paramName << ['visitors','omitVisitors']
    }

    def "can configure filters optional parameter"(String paramName) {
        def task = setupWithMain()

        expect: !hasArgument(task, "-${paramName}")

        when:
            task["${paramName}Filter"] = project.file('shouldNotExist.txt')
            task.generateSpec()
        then: thrown(InvalidUserDataException)

        when:
        File validFile = File.createTempFile(paramName, "txt")
        task["${paramName}Filter"] = validFile

        then:
        println task.generateSpec().getArguments()
        hasArgument(task, "-${paramName}")
        hasArgument(task, validFile.getPath())

        where: paramName << ['include', 'exclude']

    }
}
