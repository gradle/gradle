/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.samples

import gradlebuild.basics.toLowerCase
import gradlebuild.basics.toUpperCase
import org.gradle.api.file.Directory
import org.gradle.buildinit.plugins.internal.CompositeProjectInitDescriptor
import org.gradle.buildinit.plugins.internal.InitSettings
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption

import java.util.stream.Collectors


object SamplesGenerator {

    fun generate(type: String, modularization: ModularizationOption, templateFolder: Directory, target: Directory, projectLayoutSetupRegistry: ProjectLayoutSetupRegistry) {
        val descriptor = projectLayoutSetupRegistry[type] as CompositeProjectInitDescriptor
        val projectName = "demo"
        val packageName = if (descriptor.supportsPackage()) projectName else null
        val testFramework = if (modularization == ModularizationOption.WITH_LIBRARY_PROJECTS) BuildInitTestFramework.JUNIT_JUPITER else descriptor.defaultTestFramework

        // clear the target directory to remove renamed files and reset the README file
        target.asFile.deleteRecursively()

        val groovyDslSettings = InitSettings(projectName, descriptor.componentType.defaultProjectNames, modularization, BuildInitDsl.GROOVY, packageName, testFramework, target.dir("groovy"))
        val kotlinDslSettings = InitSettings(projectName, descriptor.componentType.defaultProjectNames, modularization, BuildInitDsl.KOTLIN, packageName, testFramework, target.dir("kotlin"))

        val specificContentId = if (descriptor.language === Language.CPP || descriptor.language === Language.SWIFT) {
            "native-" + descriptor.componentType.toString()
        } else {
            descriptor.componentType.toString()
        }

        val comments = descriptor.generateWithExternalComments(groovyDslSettings)
        descriptor.generateWithExternalComments(kotlinDslSettings)

        if (modularization == ModularizationOption.SINGLE_PROJECT) {
            generateSingleProjectReadme(specificContentId, templateFolder, groovyDslSettings, comments, descriptor, projectLayoutSetupRegistry)
        } else {
            generateMultiProjectReadme(specificContentId, templateFolder, groovyDslSettings, comments, descriptor, projectLayoutSetupRegistry)
        }
    }

    private
    fun generateSingleProjectReadme(specificContentId: String, templateFolder: Directory, settings: InitSettings, comments: Map<String, List<String>>, descriptor: CompositeProjectInitDescriptor, projectLayoutSetupRegistry: ProjectLayoutSetupRegistry) {
        generateReadmeFragment(templateFolder, "common-body", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateReadmeFragment(templateFolder, "$specificContentId-body", settings, comments, descriptor, projectLayoutSetupRegistry)
        if (descriptor.language === Language.JAVA && descriptor.componentType === ComponentType.LIBRARY) {
            generateReadmeFragment(templateFolder, "$specificContentId-api-docs", settings, comments, descriptor, projectLayoutSetupRegistry)
        }
        generateReadmeFragment(templateFolder, "common-summary", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateReadmeFragment(templateFolder, "$specificContentId-summary", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateOutput(templateFolder, specificContentId, settings, descriptor, projectLayoutSetupRegistry)
    }

    private
    fun generateMultiProjectReadme(specificContentId: String, templateFolder: Directory, settings: InitSettings, comments: Map<String, List<String>>, descriptor: CompositeProjectInitDescriptor, projectLayoutSetupRegistry: ProjectLayoutSetupRegistry) {
        generateReadmeFragment(templateFolder, "multi-common-body", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateReadmeFragment(templateFolder, "$specificContentId-body", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateReadmeFragment(templateFolder, "common-summary", settings, comments, descriptor, projectLayoutSetupRegistry)
        generateReadmeFragment(templateFolder, "multi-common-summary", settings, comments, descriptor, projectLayoutSetupRegistry)
    }

    private
    fun generateReadmeFragment(templateFolder: Directory, templateFragment: String, settings: InitSettings, comments: Map<String, List<String>>, descriptor: CompositeProjectInitDescriptor, projectLayoutSetupRegistry: ProjectLayoutSetupRegistry) {

        val languages = projectLayoutSetupRegistry.getLanguagesFor(descriptor.componentType)
        var exampleClass = if (descriptor.componentType === ComponentType.LIBRARY) "Library" else "App"
        val testFileSuffix = if (descriptor.language === Language.SCALA) "Suite" else "Test"
        val sourceFile: String
        val testSourceFile: String
        val sourceFileTree: String
        val testSourceFileTree: String
        when {
            descriptor.language === Language.CPP -> {
                exampleClass = if (descriptor.componentType === ComponentType.LIBRARY) "Hello" else "Greeter"
                sourceFile = (if (descriptor.componentType === ComponentType.LIBRARY) "hello" else "app") + ".cpp"
                testSourceFile = (if (descriptor.componentType === ComponentType.LIBRARY) "hello" else "app") + "_test.cpp"
                sourceFileTree = """        │   │   └── $sourceFile
        │   └── headers
        │       └── app.h"""
                testSourceFileTree = "                └── $testSourceFile"
            }
            descriptor.language === Language.SWIFT -> {
                exampleClass = if (descriptor.componentType === ComponentType.LIBRARY) "Hello" else "Greeter"
                sourceFile = (if (descriptor.componentType === ComponentType.LIBRARY) "Hello" else "main") + ".swift"
                testSourceFile = exampleClass + "Tests.swift"
                sourceFileTree = "        │       └── $sourceFile"
                testSourceFileTree = """                └── $testSourceFile
                └── LinuxMain.swift"""
            }
            else -> {
                sourceFile = "demo/" + exampleClass + "." + descriptor.language.extension
                testSourceFile = "demo/" + exampleClass + testFileSuffix + "." + descriptor.language.extension
                sourceFileTree = """        │       └── demo
        │           └── $exampleClass.${descriptor.language.extension}"""
                testSourceFileTree = """                └── demo
                    └── $exampleClass$testFileSuffix.${descriptor.language.extension}"""
            }
        }
        val buildFileComments = comments.values.first().stream().map { c: String -> "<" + (comments.values.first().indexOf(c) + 1) + "> " + c }.collect(Collectors.joining("\n"))
        val testFrameworkChoice = if (descriptor.testFrameworks.size > 1) """
Select test framework:
  1: JUnit 4
  2: TestNG
  3: Spock
  4: JUnit Jupiter
Enter selection (default: JUnit 4) [1..4]
""" else ""
        val packageNameChoice = if (descriptor.supportsPackage()) "Source package (default: demo):\n" else ""
        val toolChain = when {
            descriptor.language === Language.SWIFT -> {
                "* An installed Swift compiler. See which link:{userManualPath}/building_swift_projects.html#sec:swift_supported_tool_chain[Swift tool chains] are supported by Gradle."
            }
            descriptor.language === Language.CPP -> {
                "* An installed {cpp} compiler. See which link:{userManualPath}/building_cpp_projects.html#sec:cpp_supported_tool_chain[{cpp} tool chains] are supported by Gradle."
            }
            else -> {
                ""
            }
        }
        val languagePluginDocsLink = if (descriptor.language === Language.KOTLIN)
            "link:https://kotlinlang.org/docs/reference/using-gradle.html[Kotlin Gradle plugin]"
        else
            "link:{userManualPath}/${descriptor.language.getName()}_plugin.html[${descriptor.language} Plugin]"

        val pluginType = if (descriptor.componentType === ComponentType.LIBRARY) "Library" else "Application"
        val configurationCacheCompatMatrixLink = "link:{userManualPath}/configuration_cache.html#config_cache:plugins:core"
        val configurationCacheCompatibility = when (descriptor.language) {
            Language.CPP -> {
                "WARNING: The {cpp} $pluginType Plugin is not compatible with the $configurationCacheCompatMatrixLink[configuration cache]."
            }
            Language.SWIFT -> {
                "WARNING: The Swift $pluginType Plugin is not compatible with the $configurationCacheCompatMatrixLink[configuration cache]."
            }
            else -> {
                ""
            }
        }

        projectLayoutSetupRegistry.templateOperationFactory.newTemplateOperation()
            .withTemplate(templateFolder.template("$templateFragment.adoc"))
            .withTarget(settings.target.file("../README.adoc").asFile)
            .withBinding("language", descriptor.language.toString().replace("C++", "{cpp}"))
            .withBinding("languageLC", descriptor.language.getName().toLowerCase())
            .withBinding("languageExtension", descriptor.language.extension)
            .withBinding("languageIndex", "" + (languages.indexOf(descriptor.language) + 1))
            .withBinding("componentType", descriptor.componentType.name.toLowerCase())
            .withBinding("componentTypeIndex", "" + (descriptor.componentType.ordinal + 1))
            .withBinding("packageNameChoice", packageNameChoice)
            .withBinding("subprojectName", settings.subprojects.first())
            .withBinding("toolChain", toolChain)
            .withBinding("exampleClass", exampleClass)
            .withBinding("sourceFile", sourceFile)
            .withBinding("testSourceFile", testSourceFile)
            .withBinding("sourceFileTree", sourceFileTree)
            .withBinding("testSourceFileTree", testSourceFileTree)
            .withBinding("testFramework", if (descriptor.defaultTestFramework == null) "" else "_" + descriptor.defaultTestFramework.toString() + "_")
            .withBinding("buildFileComments", buildFileComments)
            .withBinding("testFrameworkChoice", testFrameworkChoice)
            .withBinding("tasksExecuted", "" + tasksExecuted(descriptor))
            .withBinding("languagePluginDocsLink", "" + languagePluginDocsLink)
            .withBinding("configurationCacheCompatibility", configurationCacheCompatibility)
            .create().generate()
    }

    private
    fun generateOutput(templateFolder: Directory, templateFragment: String, settings: InitSettings, descriptor: CompositeProjectInitDescriptor, projectLayoutSetupRegistry: ProjectLayoutSetupRegistry) {
        val subprojectName = settings.subprojects.first()
        val languageName = descriptor.language.getName().substring(0, 1).toUpperCase() + descriptor.language.getName().substring(1)
        val extraCompileJava = if (descriptor.language != Language.JAVA) """
     > Task :$subprojectName:compileJava NO-SOURCE

        """.trimIndent() else ""
        val extraCompileTestJava = if (descriptor.language != Language.JAVA) """
     > Task :$subprojectName:compileTestJava NO-SOURCE

        """.trimIndent() else ""
        val nativeTestTaskPrefix = if (descriptor.language === Language.SWIFT) "xc" else "run"
        val classesUpToDate = if (descriptor.language === Language.KOTLIN) " UP-TO-DATE" else ""
        projectLayoutSetupRegistry.templateOperationFactory.newTemplateOperation()
            .withTemplate(templateFolder.template("$templateFragment-build.out"))
            .withTarget(settings.target.file("../tests/build.out").asFile)
            .withBinding("language", languageName)
            .withBinding("subprojectName", subprojectName)
            .withBinding("extraCompileJava", extraCompileJava)
            .withBinding("extraCompileTestJava", extraCompileTestJava)
            .withBinding("nativeTestTaskPrefix", nativeTestTaskPrefix)
            .withBinding("tasksExecuted", "" + tasksExecuted(descriptor))
            .withBinding("classesUpToDate", "" + classesUpToDate)
            .create().generate()
        projectLayoutSetupRegistry.templateOperationFactory.newTemplateOperation()
            .withTemplate(templateFolder.template("build.sample.conf"))
            .withTarget(settings.target.file("../tests/build.sample.conf").asFile)
            .create().generate()
    }

    private
    fun tasksExecuted(descriptor: CompositeProjectInitDescriptor): Int {
        var tasksExecuted = if (descriptor.componentType === ComponentType.LIBRARY) 4 else 7
        return tasksExecuted
    }

    private
    fun Directory.template(templateFragment: String) = file("$templateFragment.template").asFile.toURI().toURL()
}
