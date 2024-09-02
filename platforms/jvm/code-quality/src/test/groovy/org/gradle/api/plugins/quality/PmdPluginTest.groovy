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

import com.google.common.collect.ImmutableSet
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static spock.util.matcher.HamcrestSupport.that

class PmdPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(PmdPlugin)
        RepoScriptBlockUtil.configureMavenCentral(project.repositories)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "pmd 7.0.0 is loading the right dependencies"() {
        expect:
        PmdPlugin.calculateDefaultDependencyNotation("7.0.0") == ImmutableSet.of("net.sourceforge.pmd:pmd-java:7.0.0", "net.sourceforge.pmd:pmd-ant:7.0.0")
    }

    def "pmd 7.0.0-rc4 is loading the right dependencies"() {
        expect:
        PmdPlugin.calculateDefaultDependencyNotation("7.0.0-rc4") == ImmutableSet.of("net.sourceforge.pmd:pmd-java:7.0.0-rc4", "net.sourceforge.pmd:pmd-ant:7.0.0-rc4")
    }

    def "pmd 6.55.0 is loading the right dependencies"() {
        expect:
        PmdPlugin.calculateDefaultDependencyNotation("6.55.0") == Collections.singleton("net.sourceforge.pmd:pmd-java:6.55.0")
    }

    def "pmd 5.1.0 is loading the right dependencies"() {
        expect:
        PmdPlugin.calculateDefaultDependencyNotation("5.1.0") == Collections.singleton("net.sourceforge.pmd:pmd:5.1.0")
    }

    def "pmd 4.3.0 is loading the right dependencies"() {
        expect:
        PmdPlugin.calculateDefaultDependencyNotation("4.3.0") == Collections.singleton("pmd:pmd:4.3.0")
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
        extension.ruleSets.get() == ["category/java/errorprone.xml"]
        extension.ruleSetConfig == null
        extension.ruleSetFiles.empty
        extension.reportsDir.asFile.get() == project.file("build/reports/pmd")
        !extension.ignoreFailures.get()
        extension.maxFailures.get() == 0
        extension.rulesMinimumPriority.get() == 5
        extension.threads.get() == 1
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
        project.tasks.getByName("pmdMain").targetJdk.get() == targetJdk

        where:
        sourceCompatibility | targetJdk
        1.3                 | TargetJdk.VERSION_1_3
        1.4                 | TargetJdk.VERSION_1_4
        1.5                 | TargetJdk.VERSION_1_5
        1.6                 | TargetJdk.VERSION_1_6
        1.7                 | TargetJdk.VERSION_1_7
        // 1.4 is the default in the pmd plugin so we use it as a default too
        1.8                 | TargetJdk.VERSION_1_4
        1.1                 | TargetJdk.VERSION_1_4
        1.2                 | TargetJdk.VERSION_1_4
    }

    private void configuresPmdTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Pmd
        task.with {
            assert description == "Run PMD analysis for ${sourceSet.name} classes"
            source as List == sourceSet.allJava as List
            assert pmdClasspath.files == project.configurations.pmd.files
            assert ruleSets.get() == ["category/java/errorprone.xml"]
            assert ruleSetConfig == null
            assert ruleSetFiles.empty
            assert reports.xml.outputLocation.asFile.get() == project.file("build/reports/pmd/${sourceSet.name}.xml")
            assert reports.html.outputLocation.asFile.get() == project.file("build/reports/pmd/${sourceSet.name}.html")
            assert ignoreFailures == false
            assert maxFailures.get() == 0
            assert rulesMinimumPriority.get() == 5
            assert incrementalAnalysis.get() == true
            assert threads.get() == 1
        }
    }

    def "configures any additional PMD tasks"() {
        def task = project.tasks.create("pmdCustom", Pmd)

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath.files == project.configurations.pmd.files
        task.ruleSets.get() == ["category/java/errorprone.xml"]
        task.ruleSetConfig == null
        task.ruleSetFiles.empty
        task.reports.xml.outputLocation.asFile.get() == project.file("build/reports/pmd/custom.xml")
        task.reports.html.outputLocation.asFile.get() == project.file("build/reports/pmd/custom.html")
        task.ignoreFailures == false
        task.maxFailures.get() == 0
        task.rulesMinimumPriority.get() == 5
        task.incrementalAnalysis.get() == true
        task.threads.get() == 1
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
            rulesMinimumPriority = 3
            threads = 2
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
            assert pmdClasspath.files == project.configurations.pmd.files
            assert ruleSets.get() == ["java-braces", "java-unusedcode"]
            assert ruleSetConfig.asString() == "ruleset contents"
            assert ruleSetFiles.singleFile == project.file("my-ruleset.xml")
            assert reports.xml.outputLocation.asFile.get() == project.file("pmd-reports/${sourceSet.name}.xml")
            assert reports.html.outputLocation.asFile.get() == project.file("pmd-reports/${sourceSet.name}.html")
            assert ignoreFailures == true
            assert maxFailures.get() == 17
            assert rulesMinimumPriority.get() == 3
            task.threads.get() == 2
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
            rulesMinimumPriority = 3
            threads = 2
        }

        expect:
        task.description == null
        task.source.empty
        task.pmdClasspath.files == project.configurations.pmd.files
        task.ruleSets.get() == ["java-braces", "java-unusedcode"]
        task.ruleSetConfig.asString() == "ruleset contents"
        task.ruleSetFiles.singleFile == project.file("my-ruleset.xml")
        task.reports.xml.outputLocation.asFile.get() == project.file("pmd-reports/custom.xml")
        task.reports.html.outputLocation.asFile.get() == project.file("pmd-reports/custom.html")
        task.outputs.files.files == task.reports.enabled*.outputLocation.collect { it.get().asFile } as Set
        task.ignoreFailures == true
        task.maxFailures.get() == 5
        task.rulesMinimumPriority.get() == 3
        task.threads.get() == 2
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

    def "tool configuration has correct attributes"() {
        expect:
        with(project.configurations.pmd.attributes) {
            assert getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.LIBRARY
            assert getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
            assert getAttribute(Bundling.BUNDLING_ATTRIBUTE).name == Bundling.EXTERNAL
            assert getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
            assert getAttribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).name == TargetJvmEnvironment.STANDARD_JVM
        }
    }

    def "pmdAux configuration has correct attributes"() {
        expect:
        with(project.configurations.pmdAux.attributes) {
            assert getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.LIBRARY
            assert getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
            assert getAttribute(Bundling.BUNDLING_ATTRIBUTE).name == Bundling.EXTERNAL
            assert getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
            assert getAttribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).name == TargetJvmEnvironment.STANDARD_JVM
        }
    }

    def "task aux configurations have correct attributes"() {
        given:
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
        }

        when:
        project.tasks.pmdMain

        then:
        with(project.configurations.mainPmdAuxClasspath.attributes) {
            assert getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.LIBRARY
            assert getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
            assert getAttribute(Bundling.BUNDLING_ATTRIBUTE).name == Bundling.EXTERNAL
            assert getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
            assert getAttribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).name == TargetJvmEnvironment.STANDARD_JVM
        }
    }
}
