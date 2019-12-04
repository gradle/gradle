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
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.*
import static spock.util.matcher.HamcrestSupport.that

class PmdPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(PmdPlugin)
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
        extension.ruleSets == ["category/java/errorprone.xml"]
        extension.ruleSetConfig == null
        extension.ruleSetFiles.empty
        extension.reportsDir == project.file("build/reports/pmd")
        !extension.ignoreFailures
        extension.maxFailures.get() == 0
        extension.rulePriority == 5
    }

    def "configures pmd task for each source set"() {
        project.pluginManager.apply(JavaBasePlugin)
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
        project.pluginManager.apply(JavaBasePlugin)
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
            assert ruleSets == ["category/java/errorprone.xml"]
            assert ruleSetConfig == null
            assert ruleSetFiles.empty
            assert reports.xml.destination == project.file("build/reports/pmd/${sourceSet.name}.xml")
            assert reports.html.destination == project.file("build/reports/pmd/${sourceSet.name}.html")
            assert ignoreFailures == false
            assert maxFailures.get() == 0
            assert rulePriority == 5
            assert incrementalAnalysis.get() == false
        }
    }

    def "configures any additional PMD tasks"() {
        def task = project.tasks.create("pmdCustom", Pmd)

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath == project.configurations.pmd
        task.ruleSets == ["category/java/errorprone.xml"]
        task.ruleSetConfig == null
        task.ruleSetFiles.empty
        task.reports.xml.destination == project.file("build/reports/pmd/custom.xml")
        task.reports.html.destination == project.file("build/reports/pmd/custom.html")
        task.ignoreFailures == false
        task.maxFailures.get() == 0
        task.rulePriority == 5
        task.incrementalAnalysis.get() == false
    }

    def "adds pmd tasks to check lifecycle task"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.tasks['check'], dependsOn(hasItems("pmdMain", "pmdTest", "pmdOther")))
    }

    def "can customize settings via extension"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.pmd {
            sourceSets = [project.sourceSets.main]
            ruleSets = ["java-braces", "java-unusedcode"]
            ruleSetConfig = project.resources.text.fromString("ruleset contents")
            ruleSetFiles = project.getLayout().files("my-ruleset.xml")
            reportsDir = project.file("pmd-reports")
            ignoreFailures = true
            maxFailures = 17
            rulePriority = 3
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
            assert ruleSets == ["java-braces", "java-unusedcode"]
            assert ruleSetConfig.asString() == "ruleset contents"
            assert ruleSetFiles.singleFile == project.file("my-ruleset.xml")
            assert reports.xml.destination == project.file("pmd-reports/${sourceSet.name}.xml")
            assert reports.html.destination == project.file("pmd-reports/${sourceSet.name}.html")
            assert ignoreFailures == true
            assert maxFailures.get() == 17
            assert rulePriority == 3
        }
    }

    def "can customize any additional PMD tasks via extension"() {
        def task = project.tasks.create("pmdCustom", Pmd)
        project.pmd {
            ruleSets = ["java-braces", "java-unusedcode"]
            ruleSetConfig = project.resources.text.fromString("ruleset contents")
            ruleSetFiles = project.getLayout().files("my-ruleset.xml")
            reportsDir = project.file("pmd-reports")
            ignoreFailures = true
            maxFailures = 5
            rulePriority = 3
        }

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath == project.configurations.pmd
        task.ruleSets == ["java-braces", "java-unusedcode"]
        task.ruleSetConfig.asString() == "ruleset contents"
        task.ruleSetFiles.singleFile == project.file("my-ruleset.xml")
        task.reports.xml.destination == project.file("pmd-reports/custom.xml")
        task.reports.html.destination == project.file("pmd-reports/custom.html")
        task.outputs.files.files == task.reports.enabled*.destination as Set
        task.ignoreFailures == true
        task.maxFailures.get() == 5
        task.rulePriority == 3
    }

    def "configures pmd classpath based on sourcesets"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
        }
        def mainSourceSet = project.sourceSets.main
        def pmdTask = project.tasks.getByName("pmdMain")
        expect:
        pmdTask.classpath.files == (mainSourceSet.output + mainSourceSet.compileClasspath).files
    }
}
