/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ear

import groovy.xml.XmlSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

import java.util.zip.ZipFile

class EarDeploymentDescriptorLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private static final String PRODUCER = """
        abstract class Producer extends DefaultTask {
            @Input
            abstract Property<String> getContent()

            @OutputFile
            abstract RegularFileProperty getOutputFile()

            @TaskAction
            void run() {
                outputFile.get().asFile.text = content.get()
            }
        }
        def producer = tasks.register("producer", Producer) {
            content = "produced"
            outputFile = layout.buildDirectory.file("produced.txt")
        }
    """

    def setup() {
        file("module.jar").text = "module"
        file("web.war").text = "web"
        settingsFile "rootProject.name = 'root'"
        buildFile """
            plugins {
                id("ear")
            }
            dependencies {
                deploy(files("module.jar", "web.war"))
            }
        """
    }

    private String descriptor(String archive = "build/libs/root.ear", String entry = "META-INF/application.xml") {
        def zip = new ZipFile(file(archive))
        try {
            def zipEntry = zip.getEntry(entry)
            return zipEntry == null ? null : zip.getInputStream(zipEntry).text
        } finally {
            zip.close()
        }
    }

    private def parsedDescriptor(String archive = "build/libs/root.ear") {
        return new XmlSlurper().parseText(descriptor(archive))
    }

    def "the plugin's descriptor stores its values in the lazy node of the task"() {
        buildFile """
            def ear = tasks.named("ear").get()
            def lazy = ear.lazyProperties.deploymentDescriptor
            def d = ear.deploymentDescriptor
            println("same object: " + d.is(ear.deploymentDescriptor))
            println("defaults: " + [d.fileName, d.version, d.initializeInOrder, d.displayName, d.description, d.libraryDirectory, d.modules, d.securityRoles, d.moduleTypeMappings])
            println("lazy defaults: " + [lazy.fileName.get(), lazy.version.get(), lazy.initializeInOrder.get(), lazy.displayName.get(), lazy.description.getOrNull()])

            ear.deploymentDescriptor {
                applicationName = "app"
                initializeInOrder = true
                module("ejb.jar", "java")
                webModule("site.war", "/site")
                securityRole("admin")
            }
            d.modules.add(new org.gradle.plugins.ear.descriptor.internal.DefaultEarModule("added.jar"))
            d.moduleTypeMappings.put("added.jar", "connector")
            println("lazy values: " + [lazy.applicationName.get(), lazy.initializeInOrder.get(), lazy.modules.get()*.path, lazy.securityRoles.get()*.roleName, lazy.moduleTypeMappings.get()])
            println("eager values: " + [d.applicationName, d.initializeInOrder, d.modules*.path, d.securityRoles*.roleName, d.moduleTypeMappings])
        """

        when:
        succeeds("ear")

        then:
        outputContains("same object: true")
        outputContains("defaults: [application.xml, 6, false, root, null, null, [], [], [:]]")
        outputContains("lazy defaults: [application.xml, 6, false, root, null]")
        outputContains("lazy values: [app, true, [ejb.jar, site.war, added.jar], [admin], [ejb.jar:java, site.war:web, added.jar:connector]]")
        outputContains("eager values: [app, true, [ejb.jar, site.war, added.jar], [admin], [ejb.jar:java, site.war:web, added.jar:connector]]")

        and:
        def xml = parsedDescriptor()
        xml.'application-name'.text() == "app"
        xml.'display-name'.text() == "root"
        xml.'initialize-in-order'.text() == "true"
        xml.module.collect { it.children()[0].name() } == ["java", "web", "connector", "ejb", "web"]
        xml.module.collect { it.text() } == ["ejb.jar", "site.war/site", "added.jar", "module.jar", "web.warweb"]
        xml.'security-role'.'role-name'.text() == "admin"
        xml.'library-directory'.text() == "lib"
    }

    def "values set lazily reach the descriptor in the archive and read back through the eager getters"() {
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.with {
                    displayName = providers.provider { "lazy display" }
                    description = "lazy description"
                    version = "8"
                    libraryDirectory = "APP-INF/lib"
                    modules.add(providers.provider { new org.gradle.plugins.ear.descriptor.internal.DefaultEarModule("lazy.jar") })
                    moduleTypeMappings.put("lazy.jar", "java")
                    securityRoles.add(new org.gradle.plugins.ear.descriptor.internal.DefaultEarSecurityRole("lazy-role", "a role"))
                }
                doLast {
                    def d = deploymentDescriptor
                    println("eager: " + [d.displayName, d.description, d.version, d.libraryDirectory, d.modules*.path, d.moduleTypeMappings, d.securityRoles*.roleName])
                }
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("eager: [lazy display, lazy description, 8, APP-INF/lib, [lazy.jar], [lazy.jar:java], [lazy-role]]")
        def text = descriptor()
        text.contains('version="8"')
        text.contains("http://xmlns.jcp.org/xml/ns/javaee/application_8.xsd")
        def xml = new XmlSlurper().parseText(text)
        xml.'display-name'.text() == "lazy display"
        xml.description.text() == "lazy description"
        xml.'library-directory'.text() == "APP-INF/lib"
        xml.module.collect { it.children()[0].name() } == ["java", "ejb", "web"]
        xml.module.collect { it.text() } == ["lazy.jar", "module.jar", "web.warweb"]
        xml.'security-role'.'role-name'.text() == "lazy-role"
        xml.'security-role'.description.text() == "a role"
    }

    def "an eager change after a lazy binding wins, and a lazy binding after an eager change wins"() {
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = providers.provider { "lazy" }
                deploymentDescriptor.displayName = "eager"
                println("eager after lazy: " + deploymentDescriptor.displayName)

                deploymentDescriptor.description = "eager"
                lazyProperties.deploymentDescriptor.description = providers.provider { "lazy" }
                println("lazy after eager: " + deploymentDescriptor.description)

                lazyProperties.deploymentDescriptor.modules.add(new org.gradle.plugins.ear.descriptor.internal.DefaultEarModule("lazy.jar"))
                deploymentDescriptor.module("eager.jar", "java")
                println("modules: " + deploymentDescriptor.modules*.path)

                deploymentDescriptor.applicationName = null
                lazyProperties.deploymentDescriptor.applicationName.unset()
                println("unset: " + deploymentDescriptor.applicationName)
                deploymentDescriptor.version = null
                println("null version: " + deploymentDescriptor.version)
                lazyProperties.deploymentDescriptor.version.unset()
                println("unset version: " + deploymentDescriptor.version)
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("eager after lazy: eager")
        outputContains("lazy after eager: lazy")
        outputContains("modules: [lazy.jar, eager.jar]")
        outputContains("unset: null")
        outputContains("null version: null")
        outputContains("unset version: 6")
        def xml = parsedDescriptor()
        xml.'display-name'.text() == "eager"
        xml.description.text() == "lazy"
        xml.module.collect { it.text() } == ["lazy.jar", "eager.jar", "module.jar", "web.warweb"]
    }

    def "running the task does not change the configured descriptor"() {
        buildFile """
            tasks.ear {
                doLast {
                    println("after run: " + [deploymentDescriptor.modules*.path, deploymentDescriptor.libraryDirectory, lazyProperties.deploymentDescriptor.modules.get()*.path, lazyProperties.deploymentDescriptor.libraryDirectory.getOrNull()])
                }
            }
        """

        when:
        succeeds("ear")

        then:
        // On Gradle 9.9 the descriptor is null after the run, and the recorded modules and the library directory were stored in it before
        outputContains("after run: [[], null, [], null]")
        parsedDescriptor().module.collect { it.text() } == ["module.jar", "web.warweb"]
        parsedDescriptor().'library-directory'.text() == "lib"
    }

    def "an application.xml in the sources is used instead of the generated one, and no modules are recorded"() {
        file("src/main/application/META-INF/application.xml").text = """<?xml version="1.0"?>
<application version="6"><display-name>from sources</display-name></application>
"""
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = "lazy"
            }
        """

        when:
        succeeds("ear")

        then:
        descriptor().contains("<display-name>from sources</display-name>")
    }

    def "changing a descriptor value reruns the task, because the generated file is an input"() {
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = providers.gradleProperty("displayName")
            }
        """

        when:
        succeeds("ear", "-PdisplayName=one")

        then:
        executedAndNotSkipped(":ear")
        parsedDescriptor().'display-name'.text() == "one"

        when:
        succeeds("ear", "-PdisplayName=one")

        then:
        skipped(":ear")

        when:
        succeeds("ear", "-PdisplayName=two")

        then:
        executedAndNotSkipped(":ear")
        parsedDescriptor().'display-name'.text() == "two"
    }

    def "a descriptor value bound to a task output runs that task first"() {
        buildFile """
            $PRODUCER
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = producer.flatMap { it.outputFile }.map { it.asFile.text }
                lazyProperties.deploymentDescriptor.modules.add(producer.flatMap { it.outputFile }.map { new org.gradle.plugins.ear.descriptor.internal.DefaultEarModule(it.asFile.text + ".jar") })
            }
        """

        when:
        succeeds("ear")

        then:
        result.assertTaskOrder(":producer", ":ear")
        parsedDescriptor().'display-name'.text() == "produced"
        parsedDescriptor().module.collect { it.text() } == ["produced.jar", "module.jar", "web.warweb"]

        when:
        buildFile << """
            producer.configure { content = "changed" }
        """
        succeeds("ear")

        then:
        executedAndNotSkipped(":producer", ":ear")
        parsedDescriptor().'display-name'.text() == "changed"
    }

    def "a descriptor value bound to a task output of a custom descriptor needs an explicit dependency"() {
        buildFile """
            $PRODUCER
            class CustomDescriptor implements org.gradle.plugins.ear.descriptor.DeploymentDescriptor {
                @Delegate org.gradle.plugins.ear.descriptor.DeploymentDescriptor delegate
            }
            tasks.ear {
                def inner = project.objects.newInstance(org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor)
                inner.lazyProperties.displayName = producer.flatMap { it.outputFile }.map { it.asFile.text }
                deploymentDescriptor = new CustomDescriptor(delegate: inner)
            }
        """

        when:
        fails("ear")

        then:
        notExecuted(":producer")
        failure.assertHasDescription("Execution failed for task ':ear'")
        failure.assertHasCause("Could not copy application.xml to")

        when:
        buildFile << """
            tasks.ear { dependsOn(producer) }
        """
        succeeds("ear")

        then:
        result.assertTaskOrder(":producer", ":ear")
        parsedDescriptor().'display-name'.text() == "produced"
    }

    def "an Ear task without the plugin uses the lazy node once the descriptor is configured"() {
        buildFile.text = """
            tasks.register("withDescriptor", Ear) {
                archiveFileName = "with.ear"
                destinationDirectory = layout.buildDirectory.dir("out")
                from("module.jar")
                println("before: " + deploymentDescriptor)
                lazyProperties.deploymentDescriptor.displayName = "lazy before"
                deploymentDescriptor {
                    description = "eager"
                }
                println("after: " + [deploymentDescriptor.displayName, deploymentDescriptor.description, deploymentDescriptor.fileName])
            }
            tasks.register("withoutDescriptor", Ear) {
                archiveFileName = "without.ear"
                destinationDirectory = layout.buildDirectory.dir("out")
                from("module.jar")
                lazyProperties.deploymentDescriptor.displayName = "ignored"
            }
        """

        when:
        succeeds("withDescriptor", "withoutDescriptor")

        then:
        outputContains("before: null")
        outputContains("after: [lazy before, eager, application.xml]")
        def xml = parsedDescriptor("build/out/with.ear")
        xml.'display-name'.text() == "lazy before"
        xml.description.text() == "eager"
        xml.module.text() == "module.jar"
        // No libDirName without the plugin, so no library directory is written
        xml.'library-directory'.isEmpty()
        descriptor("build/out/without.ear") == null
    }

    def "an Ear task that uses the descriptor from the convention mapping ignores the lazy node until the descriptor is configured"() {
        buildFile """
            tasks.register("mapped", Ear) {
                archiveFileName = "mapped.ear"
                from("module.jar")
                lazyProperties.deploymentDescriptor.displayName = "ignored"
            }
            tasks.register("configured", Ear) {
                archiveFileName = "configured.ear"
                from("module.jar")
                lazyProperties.deploymentDescriptor.displayName = "lazy"
                deploymentDescriptor {
                    description = "eager"
                }
            }
            afterEvaluate {
                def mapped = tasks.mapped
                println("mapped: " + [mapped.deploymentDescriptor.is(mapped.deploymentDescriptor), mapped.deploymentDescriptor.displayName])
                def configured = tasks.configured
                println("configured: " + [configured.deploymentDescriptor.is(configured.deploymentDescriptor), configured.deploymentDescriptor.displayName])
            }
        """

        when:
        succeeds("mapped", "configured")

        then:
        outputContains("mapped: [false, null]")
        outputContains("configured: [true, lazy]")
        def mapped = parsedDescriptor("build/libs/mapped.ear")
        mapped.'display-name'.isEmpty()
        mapped.'library-directory'.text() == "lib"
        // Gradle 9.9 loses the recorded modules here, because each read of the mapped descriptor creates a new one
        mapped.module.collect { it.text() } == ["module.jar", "web.warweb"]
        def configured = parsedDescriptor("build/libs/configured.ear")
        configured.'display-name'.text() == "lazy"
        configured.description.text() == "eager"
        configured.module.collect { it.text() } == ["module.jar", "web.warweb"]
    }

    def "a custom descriptor implementation is used as before and the lazy node has no effect"() {
        buildFile """
            class CustomDescriptor implements org.gradle.plugins.ear.descriptor.DeploymentDescriptor {
                @Delegate org.gradle.plugins.ear.descriptor.DeploymentDescriptor delegate
            }
            tasks.ear {
                def inner = project.objects.newInstance(org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor)
                inner.displayName = "custom"
                deploymentDescriptor = new CustomDescriptor(delegate: inner)
                deploymentDescriptor {
                    description = "configured"
                }
                lazyProperties.deploymentDescriptor.displayName = "ignored"
                doLast {
                    println("custom after run: " + [deploymentDescriptor.getClass().simpleName, deploymentDescriptor.modules*.path, deploymentDescriptor.libraryDirectory])
                }
            }
        """

        when:
        succeeds("ear")

        then:
        // A custom implementation still receives the recorded modules and the library directory, as on Gradle 9.9
        outputContains("custom after run: [CustomDescriptor, [module.jar, web.war], lib]")
        def xml = parsedDescriptor()
        xml.'display-name'.text() == "custom"
        xml.description.text() == "configured"
        xml.module.collect { it.text() } == ["module.jar", "web.warweb"]
    }

    def "a null descriptor generates no descriptor"() {
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = "ignored"
                deploymentDescriptor = null
            }
        """

        when:
        succeeds("ear")

        then:
        descriptor() == null
    }

    def "a replaced descriptor keeps its values and no longer changes the task"() {
        buildFile """
            tasks.ear {
                def old = deploymentDescriptor
                def replacement = project.objects.newInstance(org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor)
                replacement.displayName = "replacement"
                deploymentDescriptor = replacement
                old.displayName = "changed on the old descriptor"
                println("old: " + old.displayName)
                println("lazy after replacement: " + lazyProperties.deploymentDescriptor.displayName.get())
                lazyProperties.deploymentDescriptor.description = "lazy"
                println("replacement: " + [deploymentDescriptor.is(replacement), replacement.description])
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("old: changed on the old descriptor")
        outputContains("lazy after replacement: replacement")
        outputContains("replacement: [true, lazy]")
        def xml = parsedDescriptor()
        xml.'display-name'.text() == "replacement"
        xml.description.text() == "lazy"
    }

    def "fileName set eagerly reads the named file, set lazily it only names the generated file"() {
        file("META-INF/eager.xml").text = """<?xml version="1.0"?>
<application version="6"><display-name>read from eager.xml</display-name></application>
"""
        file("META-INF/lazy.xml").text = """<?xml version="1.0"?>
<application version="6"><display-name>read from lazy.xml</display-name></application>
"""
        buildFile """
            tasks.register("eagerName", Ear) {
                archiveFileName = "eager.ear"
                deploymentDescriptor {
                    fileName = "eager.xml"
                }
            }
            tasks.register("lazyName", Ear) {
                archiveFileName = "lazy.ear"
                deploymentDescriptor { }
                lazyProperties.deploymentDescriptor.fileName = "lazy.xml"
            }
        """

        when:
        succeeds("eagerName", "lazyName")

        then:
        descriptor("build/libs/eager.ear", "META-INF/eager.xml").contains("<display-name>read from eager.xml</display-name>")
        !descriptor("build/libs/lazy.ear", "META-INF/lazy.xml").contains("display-name")
    }

    def "Kotlin DSL forms configure the lazy descriptor"() {
        buildFile.delete()
        buildKotlinFile """
            plugins {
                ear
            }
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = "kotlin"
                lazyProperties.deploymentDescriptor.initializeInOrder = true
                lazyProperties.deploymentDescriptor.moduleTypeMappings.put("a.jar", "java")
                deploymentDescriptor {
                    description = "eager"
                }
                doLast {
                    println("kotlin: " + listOf(deploymentDescriptor.displayName, deploymentDescriptor.initializeInOrder, deploymentDescriptor.moduleTypeMappings))
                }
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("kotlin: [kotlin, true, {a.jar=java}]")
        def xml = parsedDescriptor()
        xml.'display-name'.text() == "kotlin"
        xml.description.text() == "eager"
    }

    def "descriptor values bound lazily survive the configuration cache"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        buildFile """
            tasks.ear {
                lazyProperties.deploymentDescriptor.displayName = providers.gradleProperty("displayName")
                lazyProperties.deploymentDescriptor.securityRoles.add(new org.gradle.plugins.ear.descriptor.internal.DefaultEarSecurityRole("cc-role"))
                deploymentDescriptor {
                    description = "eager"
                    withXml { it.asNode().appendNode("custom-element", "from withXml") }
                }
                doLast {
                    println("identity: " + deploymentDescriptor.lazyProperties.is(lazyProperties.deploymentDescriptor))
                }
            }
        """

        when:
        succeeds("ear", "--configuration-cache", "-PdisplayName=one")

        then:
        configurationCache.assertStateStored()
        outputContains("identity: true")
        def xml = parsedDescriptor()
        xml.'display-name'.text() == "one"
        xml.description.text() == "eager"
        xml.'security-role'.'role-name'.text() == "cc-role"
        xml.'custom-element'.text() == "from withXml"
        xml.module.collect { it.text() } == ["module.jar", "web.warweb"]

        when:
        file("build/libs/root.ear").delete()
        succeeds("ear", "--configuration-cache", "-PdisplayName=one")

        then:
        configurationCache.assertStateLoaded()
        outputContains("identity: true")
        def reloaded = parsedDescriptor()
        reloaded.'display-name'.text() == "one"
        reloaded.description.text() == "eager"
        reloaded.'custom-element'.text() == "from withXml"
        reloaded.module.collect { it.text() } == ["module.jar", "web.warweb"]
    }
}
