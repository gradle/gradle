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

class PmdPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(PmdPlugin)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
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
        extension.reportsDir == project.file("build/reports/pmd")
        !extension.ignoreFailures
    }

    def "configures pmd task for each source set"() {
        project.plugins.apply(JavaBasePlugin)
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

    def "configures pmd targetjdk based on sourcecompatibilityLevel"() {
        project.plugins.apply(JavaBasePlugin)
        when:
        project.setSourceCompatibility(sourceCompatibility)
        project.sourceSets {
            main
        }
        then:
        project.tasks.getByName("pmdMain").targetJdk == targetJdk

        where:
        sourceCompatibility | targetJdk
        1.3                 | TargetJdk.VERSION_1_3
        1.4                 | TargetJdk.VERSION_1_4
        1.5                 | TargetJdk.VERSION_1_5
        1.6                 | TargetJdk.VERSION_1_6
        1.7                 | TargetJdk.VERSION_1_7
        // 1.4 is the default in the pmd plugin so we use it as a default too
        1.8 | TargetJdk.VERSION_1_4
        1.1 | TargetJdk.VERSION_1_4
        1.2 | TargetJdk.VERSION_1_4
    }

    private void configuresPmdTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Pmd
        task.with {
            assert description == "Run PMD analysis for ${sourceSet.name} classes"
            source as List == sourceSet.allJava as List
            assert pmdClasspath == project.configurations.pmd
            assert ruleSets == ["basic"]
            assert ruleSetFiles.empty
            assert reports.xml.destination == project.file("build/reports/pmd/${sourceSet.name}.xml")
            assert reports.html.destination == project.file("build/reports/pmd/${sourceSet.name}.html")
            assert ignoreFailures == false
        }
    }

    def "configures any additional PMD tasks"() {
        def task = project.tasks.create("pmdCustom", Pmd)

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath == project.configurations.pmd
        task.ruleSets == ["basic"]
        task.ruleSetFiles.empty
        task.reports.xml.destination == project.file("build/reports/pmd/custom.xml")
        task.reports.html.destination == project.file("build/reports/pmd/custom.html")
        task.ignoreFailures == false
    }

    def "adds pmd tasks to check lifecycle task"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.tasks['check'], dependsOn(hasItems("pmdMain", "pmdTest", "pmdOther")))
    }

    def "can customize settings via extension"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.pmd {
            sourceSets = [project.sourceSets.main]
            ruleSets = ["braces", "unusedcode"]
            ruleSetFiles = project.files("my-ruleset.xml")
            reportsDir = project.file("pmd-reports")
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
            source as List == sourceSet.allJava as List
            assert pmdClasspath == project.configurations.pmd
            assert ruleSets == ["braces", "unusedcode"]
            assert ruleSetFiles.files == project.files("my-ruleset.xml").files
            assert reports.xml.destination == project.file("pmd-reports/${sourceSet.name}.xml")
            assert reports.html.destination == project.file("pmd-reports/${sourceSet.name}.html")
            assert ignoreFailures == true
        }
    }

    def "can customize any additional PMD tasks via extension"() {
        def task = project.tasks.create("pmdCustom", Pmd)
        project.pmd {
            ruleSets = ["braces", "unusedcode"]
            ruleSetFiles = project.files("my-ruleset.xml")
            reportsDir = project.file("pmd-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath == project.configurations.pmd
        task.ruleSets == ["braces", "unusedcode"]
        task.ruleSetFiles.files == project.files("my-ruleset.xml").files
        task.reports.xml.destination == project.file("pmd-reports/custom.xml")
        task.reports.html.destination == project.file("pmd-reports/custom.html")
        task.outputs.files.files == task.reports.enabled*.destination as Set
        task.ignoreFailures == true
    }

}
