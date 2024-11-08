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

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.maven.MavenFileRepository

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static spock.util.matcher.HamcrestSupport.that

class CheckstylePluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(CheckstylePlugin)
        project.file("config/checkstyle").mkdirs()
        project.file("custom").mkdirs()
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
        extension.configFile.get().getAsFile() == project.file("config/checkstyle/checkstyle.xml")
        extension.configDirectory.get().getAsFile() == project.file("config/checkstyle")
        extension.config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
        extension.configProperties.get() == [:]
        extension.reportsDir.asFile.get() == project.file("build/reports/checkstyle")
        !extension.ignoreFailures.get()
    }

    def "configures checkstyle task for each source set"() {
        project.pluginManager.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }
        publishDefaultCheckstyle()

        expect:
        configuresCheckstyleTask("checkstyleMain", project.sourceSets.main)
        configuresCheckstyleTask("checkstyleTest", project.sourceSets.test)
        configuresCheckstyleTask("checkstyleOther", project.sourceSets.other)
    }

    private void configuresCheckstyleTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof Checkstyle
        task.with {
            assert description == "Run Checkstyle analysis for ${sourceSet.name} classes".toString()
            assert checkstyleClasspath.files == project.configurations.checkstyle.files
            assert classpath.files == (sourceSet.output + sourceSet.compileClasspath).files
            assert configFile.get().getAsFile() == project.file("config/checkstyle/checkstyle.xml")
            assert configDirectory.get().getAsFile() == project.file("config/checkstyle")
            assert config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
            assert configProperties.get() == [:]
            assert reports.xml.outputLocation.asFile.get() == project.file("build/reports/checkstyle/${sourceSet.name}.xml")
            assert reports.html.outputLocation.asFile.get() == project.file("build/reports/checkstyle/${sourceSet.name}.html")
            assert !ignoreFailures
            assert showViolations
            assert maxErrors.get() == 0
            assert maxWarnings.get() == Integer.MAX_VALUE
        }
    }

    def "configures any additional checkstyle tasks"() {
        def task = project.tasks.create("checkstyleCustom", Checkstyle)
        publishDefaultCheckstyle()

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath.files == project.configurations.checkstyle.files
        task.configFile.get().getAsFile() == project.file("config/checkstyle/checkstyle.xml")
        task.configDirectory.get().getAsFile() == project.file("config/checkstyle")
        task.config.inputFiles.singleFile == project.file("config/checkstyle/checkstyle.xml")
        task.configProperties.get() == [:]
        task.reports.xml.outputLocation.asFile.get() == project.file("build/reports/checkstyle/custom.xml")
        task.reports.html.outputLocation.asFile.get() == project.file("build/reports/checkstyle/custom.html")
        task.reports.sarif.outputLocation.asFile.get() == project.file("build/reports/checkstyle/custom.sarif")
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
        ((CheckstyleExtension) project.checkstyle).with {
            sourceSets = [project.sourceSets.main]
            config = project.resources.text.fromFile("checkstyle-config")
            configDirectory.set(project.file("custom"))
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
            showViolations = true
            maxErrors = 1
            maxWarnings = 1000
        }
        publishDefaultCheckstyle()

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
            assert checkstyleClasspath.files == project.configurations["checkstyle"].files
            assert configFile.get().getAsFile() == project.file("checkstyle-config")
            assert configDirectory.get().getAsFile() == project.file("custom")
            assert config.inputFiles.singleFile == project.file("checkstyle-config")
            assert configProperties.get() == [foo: "foo"]
            assert reports.xml.outputLocation.asFile.get() == project.file("checkstyle-reports/${sourceSet.name}.xml")
            assert reports.html.outputLocation.asFile.get() == project.file("checkstyle-reports/${sourceSet.name}.html")
            assert ignoreFailures
            assert showViolations.get()
            assert maxErrors.get() == 1
            assert maxWarnings.get() == 1000
        }
    }

    def "can customize any additional checkstyle tasks via extension"() {
        def task = project.tasks.create("checkstyleCustom", Checkstyle)
        ((CheckstyleExtension) project.checkstyle).with {
            config = project.resources.text.fromFile("checkstyle-config")
            configDirectory.set(project.file("custom"))
            configProperties = [foo: "foo"]
            reportsDir = project.file("checkstyle-reports")
            ignoreFailures = true
        }
        publishDefaultCheckstyle()

        expect:
        task.description == null
        task.source.isEmpty()
        task.checkstyleClasspath.files == project.configurations.checkstyle.files
        task.configFile.get().getAsFile() == project.file("checkstyle-config")
        task.configDirectory.get().getAsFile() == project.file("custom")
        task.config.inputFiles.singleFile == project.file("checkstyle-config")
        task.configProperties.get() == [foo: "foo"]
        task.reports.xml.outputLocation.asFile.get() == project.file("checkstyle-reports/custom.xml")
        task.reports.html.outputLocation.asFile.get() == project.file("checkstyle-reports/custom.html")
        task.reports.sarif.outputLocation.asFile.get() == project.file("checkstyle-reports/custom.sarif")
        task.ignoreFailures
    }

    def "can use legacy configFile extension property"() {
        project.pluginManager.apply(JavaPlugin)

        ((CheckstyleExtension) project.checkstyle).with {
            configFile = project.file("checkstyle-config")
        }

        expect:
        project.checkstyle.configFile.get().getAsFile() == project.file("checkstyle-config") // computed property
        project.tasks.checkstyleMain.configFile.get().getAsFile() == project.file("checkstyle-config")
        project.tasks.checkstyleTest.configFile.get().getAsFile() == project.file("checkstyle-config")
    }

    def "changing the config dir changes the config file location"() {
        ((CheckstyleExtension) project.checkstyle).with {
            configDirectory.set(project.file("custom"))
        }
        expect:
        project.checkstyle.configFile.get().getAsFile() == project.file("custom/checkstyle.xml") // computed property
    }

    def "tool configuration has correct attributes"() {
        expect:
        with(project.configurations.checkstyle.attributes) {
            assert getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.LIBRARY
            assert getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
            assert getAttribute(Bundling.BUNDLING_ATTRIBUTE).name == Bundling.EXTERNAL
            assert getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
            assert getAttribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).name == TargetJvmEnvironment.STANDARD_JVM
        }
    }

    private void publishDefaultCheckstyle() {
        MavenFileRepository repo = new MavenFileRepository(temporaryFolder.createDir("repo"))
        project.repositories {
            maven {
                url repo.uri
            }
        }
        repo.module("com.puppycrawl.tools", "checkstyle", CheckstylePlugin.DEFAULT_CHECKSTYLE_VERSION).publish()
    }
}
