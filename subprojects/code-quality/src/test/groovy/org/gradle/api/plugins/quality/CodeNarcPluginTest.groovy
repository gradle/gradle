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
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.util.HelperUtil
import spock.lang.Specification
import static org.gradle.util.Matchers.dependsOn
import static org.hamcrest.Matchers.hasItems
import static spock.util.matcher.HamcrestSupport.that
import org.gradle.api.plugins.ReportingBasePlugin

class CodeNarcPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(CodeNarcPlugin)
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
        codenarc.configFile == project.file("config/codenarc/codenarc.xml")
        codenarc.reportFormat == "html"
        codenarc.reportsDir == project.file("build/reports/codenarc")
        codenarc.sourceSets == []
        !codenarc.ignoreFailures
    }

    def "adds codenarc task for each source set"() {
        project.plugins.apply(GroovyBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        configuresCodeNarcTask("codenarcMain", project.sourceSets.main)
        configuresCodeNarcTask("codenarcTest", project.sourceSets.test)
        configuresCodeNarcTask("codenarcOther", project.sourceSets.other)
    }

    private void configuresCodeNarcTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof CodeNarc
        task.with {
            assert description == "Run CodeNarc analysis for ${sourceSet.name} classes"
            assert source as List == sourceSet.allGroovy  as List
            assert codenarcClasspath == project.configurations.codenarc
            assert configFile == project.file("config/codenarc/codenarc.xml")
            assert reportFormat == "html"
            assert reportFile == project.file("build/reports/codenarc/${sourceSet.name}.html")
            assert ignoreFailures == false
        }
    }

    def "can customize per-source-set tasks via extension"() {
        project.plugins.apply(GroovyBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.codenarc {
            checkTasks = ["codenarcMain"]
            configFile = project.file("codenarc-config")
            reportFormat = "xml"
            reportsDir = project.file("codenarc-reports")
            ignoreFailures = true
        }

        expect:
        hasCustomizedSettings("codenarcMain", project.sourceSets.main)
        hasCustomizedSettings("codenarcTest", project.sourceSets.test)
        hasCustomizedSettings("codenarcOther", project.sourceSets.other)
    }

    private void hasCustomizedSettings(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof CodeNarc
        task.with {
            assert description == "Run CodeNarc analysis for ${sourceSet.name} classes"
            assert source as List == sourceSet.allGroovy as List
            assert codenarcClasspath == project.configurations.codenarc
            assert configFile == project.file("codenarc-config")
            assert reportFormat == "xml"
            assert reportFile == project.file("codenarc-reports/${sourceSet.name}.xml")
            assert ignoreFailures == true
        }
    }

    def "configures any additional codenarc tasks"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath == project.configurations.codenarc
        task.configFile == project.file("config/codenarc/codenarc.xml")
        task.reportFormat == "html"
        task.reportFile == project.file("build/reports/codenarc/custom.html")
        task.ignoreFailures == false
    }

    def "can customize additional tasks via extension"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)

        project.codenarc {
            configFile = project.file("codenarc-config")
            reportFormat = "xml"
            reportsDir = project.file("codenarc-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath == project.configurations.codenarc
        task.configFile == project.file("codenarc-config")
        task.reportFormat == "xml"
        task.reportFile == project.file("codenarc-reports/custom.xml")
        task.ignoreFailures == true
    }
    
    def "adds codenarc tasks from each source sets to check lifecycle task"() {
        project.plugins.apply(GroovyBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.tasks.create("codenarcCustom", CodeNarc)
        
        expect:
        that(project.check, dependsOn(hasItems("codenarcMain", "codenarcTest", "codenarcOther")))
    }

    def "can customize which tasks are added to check lifecycle task"() {
        project.plugins.apply(GroovyBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.tasks.create("codenarcCustom", CodeNarc)

        project.codenarc {
            sourceSets = [project.sourceSets.main]
        }

        expect:
        that(project.check, dependsOn(hasItems("codenarcMain")))
    }

    def "can customize task directly"() {
        CodeNarc task = project.tasks.create("codenarcCustom", CodeNarc)

        task.reports.xml {
            enabled true
            destination "build/foo.xml" 
        }
        
        expect:
        task.reports {
            assert enabled == [html, xml] as Set
            assert xml.destination == project.file("build/foo.xml")
        }
    }
}
