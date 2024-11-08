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
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.maven.MavenFileRepository

class CodeNarcPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(CodeNarcPlugin)
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
        codenarc.config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
        codenarc.configFile == project.file("config/codenarc/codenarc.xml")
        codenarc.maxPriority1Violations.get() == 0
        codenarc.maxPriority2Violations.get() == 0
        codenarc.maxPriority3Violations.get() == 0
        codenarc.reportFormat.get() == "html"
        codenarc.reportsDir.asFile.get() == project.file("build/reports/codenarc")
        codenarc.sourceSets.get() == []
        !codenarc.ignoreFailures.get()
    }

    def "configures any additional codenarc tasks"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)
        MavenFileRepository repo = new MavenFileRepository(temporaryFolder.createDir("repo"))
        project.repositories {
            maven {
                url repo.uri
            }
        }
        repo.module("org.codenarc", "CodeNarc", CodeNarcPlugin.STABLE_VERSION).publish()
        repo.module("org.codenarc", "CodeNarc", CodeNarcPlugin.STABLE_VERSION_WITH_GROOVY4_SUPPORT).publish()

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath.files == project.configurations.codenarc.files
        task.config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
        task.configFile == project.file("config/codenarc/codenarc.xml")
        task.maxPriority1Violations.get() == 0
        task.maxPriority2Violations.get() == 0
        task.maxPriority3Violations.get() == 0
        task.reports.enabled*.name == ["html"]
        task.reports.html.outputLocation.asFile.get() == project.file("build/reports/codenarc/custom.html")
        task.ignoreFailures.get() == false
    }

    def "can customize additional tasks via extension"() {
        def task = project.tasks.create("codenarcCustom", CodeNarc)
        MavenFileRepository repo = new MavenFileRepository(temporaryFolder.createDir("repo"))
        project.repositories {
            maven {
                url repo.uri
            }
        }
        repo.module("org.codenarc", "CodeNarc", CodeNarcPlugin.STABLE_VERSION).publish()
        repo.module("org.codenarc", "CodeNarc", CodeNarcPlugin.STABLE_VERSION_WITH_GROOVY4_SUPPORT).publish()

        project.codenarc {
            config = project.resources.text.fromFile("codenarc-config")
            maxPriority1Violations = 10
            maxPriority2Violations = 50
            maxPriority3Violations = 200
            reportFormat = "xml"
            reportsDir = project.file("codenarc-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.source.isEmpty()
        task.codenarcClasspath.files == project.configurations.codenarc.files
        task.config.inputFiles.singleFile == project.file("codenarc-config")
        task.configFile == project.file("codenarc-config")
        task.maxPriority1Violations.get() == 10
        task.maxPriority2Violations.get() == 50
        task.maxPriority3Violations.get() == 200
        task.reports.enabled*.name == ["xml"]
        task.reports.xml.outputLocation.asFile.get() == project.file("codenarc-reports/custom.xml")
        task.ignoreFailures.get() == true
    }

    def "can customize task directly"() {
        CodeNarc task = project.tasks.create("codenarcCustom", CodeNarc)

        task.reports.xml {
            required = true
            outputLocation = project.file("build/foo.xml")
        }

        expect:
        task.reports {
            assert enabled == [html, xml] as Set
            assert xml.outputLocation.asFile.get() == project.file("build/foo.xml")
        }
    }

    def "tool configuration has correct attributes"() {
        expect:
        with(project.configurations.codenarc.attributes) {
            assert getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.LIBRARY
            assert getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
            assert getAttribute(Bundling.BUNDLING_ATTRIBUTE).name == Bundling.EXTERNAL
            assert getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).name == LibraryElements.JAR
            assert getAttribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE).name == TargetJvmEnvironment.STANDARD_JVM
        }
    }
}
