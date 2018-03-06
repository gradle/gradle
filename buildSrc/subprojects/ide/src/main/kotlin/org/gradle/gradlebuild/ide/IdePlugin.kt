/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.gradlebuild.ide

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.gradle.gradlebuild.docs.PegDown

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

import java.io.File

import accessors.*
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.ProjectGroups
import org.gradle.gradlebuild.ProjectGroups.projectsRequiringJava8
import org.gradle.kotlin.dsl.*


private
const val ideConfigurationBaseName = "ideConfiguration"

//TODO move into ide
open class IdePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        configureExtensionForAllProjects()
        configureEclipseForAllProjects()
        configureIdeaForAllProjects()
        configureIdeaForRootProject()
    }

    private
    fun Project.configureExtensionForAllProjects() = allprojects {
        extensions.create<IdeExtension>(ideConfigurationBaseName, this)
    }

    private
    fun Project.configureEclipseForAllProjects() = allprojects {
        apply {
            plugin("eclipse")
        }

        plugins.withType<JavaPlugin> {
            eclipse {
                classpath {
                    file.whenMerged(Action<Classpath> {
                        //There are classes in here not designed to be compiled, but just used in our testing
                        entries.removeAll { (it as AbstractClasspathEntry).path.contains("src/integTest/resources") }
                        //Workaround for some projects referring to themselves as dependent projects
                        entries.removeAll { (it as AbstractClasspathEntry).path.contains("$project.name") && it.kind == "src" }
                        // Remove references to libraries in the build folder
                        entries.removeAll { (it as AbstractClasspathEntry).path.contains("$project.name/build") && it.kind == "lib" }
                        // Remove references to other project's binaries
                        entries.removeAll { (it as AbstractClasspathEntry).path.contains("/subprojects") && it.kind == "lib" }
                        // Add needed resources for running gradle as a non daemon java application
                        entries.add(SourceFolder("build/generated-resources/main", null))
                        if (file("build/generated-resources/test").exists()) {
                            entries.add(SourceFolder("build/generated-resources/test", null))
                        }
                    })
                }
            }
        }
    }

    private
    fun Project.configureIdeaForAllProjects() = allprojects {
        apply {
            plugin("idea")
        }
        idea {
            module {
                configureLanguageLevel(this)
                iml {
                    whenMerged(Action<Module> {
                        removeGradleBuildOutputDirectories(this)
                    })
                    withXml {
                        withJsoup {
                            configureSourceFolders(it)
                        }
                    }
                }
            }
        }
    }

    private
    fun Project.configureIdeaForRootProject() {
        idea {
            module {
                excludeDirs = excludeDirs + rootExcludeDirs
            }

            project {
                wildcards.add("?*.gradle")
                vcs = "Git"
                ipr {
                    withXml {
                        withJsoup { document ->
                            val projectElement = document.getElementsByTag("project").first()
                            configureCompilerConfiguration(projectElement)
                            configureGradleSettings(projectElement)
                            configureCopyright(projectElement)
                            projectElement.removeBySelector("component[name=ProjectCodeStyleSettingsManager]")
                                .append(CODE_STYLE_SETTINGS)
                            projectElement.removeBySelector("component[name=GroovyCompilerProjectConfiguration]")
                                .append(GROOVY_COMPILER_SETTINGS)
                            configureFrameworkDetectionExcludes(projectElement)
                            configureBuildSrc(projectElement)
                        }
                    }
                }
                workspace {
                    iws {
                        withXml {
                            withJsoup { document ->
                                val projectElement = document.getElementsByTag("project").first()
                                projectElement.createOrEmptyOutChildElement("CompilerWorkspaceConfiguration")
                                    .appendElement("option")
                                    .attr("name", "COMPILER_PROCESS_HEAP_SIZE")
                                    .attr("value", "2048")
                                val runManagerComponent = projectElement.select("component[name=RunManager]")
                                    .first()
                                configureJunitRunConfiguration(runManagerComponent)
                                configureGradleRunConfigurations(runManagerComponent)
                            }
                        }
                    }
                }
            }
        }
    }

    private
    fun Project.configureGradleSettings(projectElement: Element) {
        projectElement.removeBySelector("component[name=GradleSettings]")
            .appendElement("component")
            .attr("name", "GradleSettings")
            .appendElement("option")
            .attr("SDK_HOME", gradle.gradleHomeDir!!.absolutePath)
    }

    private
    fun configureGradleRunConfigurations(runManagerComponent: org.jsoup.nodes.Element) {
        runManagerComponent.attr("selected", "Application.Gradle")
        runManagerComponent.removeBySelector("configuration[name=gradle]")
            .append(GRADLE_CONFIGURATION)
        val gradleRunners = mapOf(
            "Regenerate IDEA metadata" to "idea",
            "Regenerate Int Test Image" to "prepareVersionsInfo intTestImage publishLocalArchives")
        gradleRunners.forEach { runnerName, commandLine ->
            runManagerComponent.removeBySelector("configuration[name=$runnerName]")
                .append(getGradleRunnerConfiguration(runnerName, commandLine))
        }
        val remoteDebugConfigurationName = "Remote debug port 5005"
        configureRemoteDebugConfiguration(runManagerComponent, remoteDebugConfigurationName)
        configureListItems(remoteDebugConfigurationName, gradleRunners, runManagerComponent)
    }

    private
    fun configureListItems(remoteDebugConfigurationName: String, gradleRunners: Map<String, String>, runManagerComponent: Element) {
        val listItemValues = mutableListOf("Application.Gradle", remoteDebugConfigurationName)
        listItemValues += gradleRunners.values
        val list = runManagerComponent.removeBySelector("list")
            .appendElement("list")
            .attr("size", listItemValues.size.toString())
        listItemValues.forEachIndexed { index, itemValue ->
            list.appendElement("item")
                .attr("index", index.toString())
                .attr("class", "java.lang.String")
                .attr("itemvalue", itemValue)
        }
    }

    private
    fun configureRemoteDebugConfiguration(runManagerComponent: org.jsoup.nodes.Element, configurationName: String) {
        runManagerComponent.removeBySelector("configuration[name=$configurationName]").append("""
                <configuration default="false" name="$configurationName" type="Remote" factoryName="Remote">
                  <option name="USE_SOCKET_TRANSPORT" value="true" />
                  <option name="SERVER_MODE" value="false" />
                  <option name="SHMEM_ADDRESS" value="javadebug" />
                  <option name="HOST" value="localhost" />
                  <option name="PORT" value="5005" />
                  <method />
                </configuration>
            """)
    }

    private
    fun Project.configureJunitRunConfiguration(runManagerComponent: org.jsoup.nodes.Element) {
        val junitConfiguration = runManagerComponent.select("configuration[type=JUnit]").first()
        val junitVmParametersOption = junitConfiguration.select("option[name=VM_PARAMETERS]").first()
        junitVmParametersOption.attr("value", getDefaultJunitVmParameter(project("docs")))
        val lang = System.getenv("LANG") ?: "en_US.UTF-8"
        junitConfiguration.select("envs").first()
            .createOrEmptyOutChildElement("env")
            .attr("name", "LANG")
            .attr("value", lang)
    }

    private
    fun configureSourceFolders(document: Document) {
        val sourceFolders = document
            .select("component[name=NewModuleRootManager]").first()
            .select("content").first()
            .select("sourceFolder[url$=/resources]")

        sourceFolders.forEach {
            it.attributes().apply {
                val isTestSource = get("isTestSource") == "true"
                remove("isTestSource")
                put("type", if (isTestSource) "java-test-resource" else "java-resource")
            }
        }
    }

    private
    fun Project.configureLanguageLevel(ideaModule: IdeaModule) {
        @Suppress("UNCHECKED_CAST")
        val ideaLanguageLevel =
            if (ideaModule.project in projectsRequiringJava8) "1.8"
            else "1.6"
        // Force everything to Java 6, pending detangling some int test cycles or switching to project-per-source-set mapping
        ideaModule.languageLevel = IdeaLanguageLevel(ideaLanguageLevel)
        ideaModule.targetBytecodeVersion = JavaVersion.toVersion(ideaLanguageLevel)
    }

    private
    fun removeGradleBuildOutputDirectories(module: Module) {
        module.dependencies.removeAll {
            it is ModuleLibrary &&
                it.classes.any {
                    // remove all Gradle build output directories from all scopes
                    it.url.contains("/build/classes/") ||
                        it.url.contains("/build/resources/") ||
                        // remove possible resource directories from all scopes
                        // replaced by java-resource/java-test-resource
                        it.url.contains("${'$'}MODULE_DIR$/src/") ||
                        // keep for build/generated-resources/main/*-classpath.properties
                        // required by DefaultModuleRegistry
                        it.url.contains("${'$'}MODULE_DIR$/build/") && !it.url.contains("generated-resources")
                }
        }
        // remove all build directories from sourceFolders
        // f.e. buildInit module contains such a sourceFolder
        module.sourceFolders.removeAll {
            it.url.contains("${'$'}MODULE_DIR$/build/")
        }
    }

    private
    fun Project.configureBuildSrc(root: Element) {
        val buildSrcModuleFile = "buildSrc/buildSrc.iml"
        val projectModuleManager = root.select("component[name=ProjectModuleManager]").first()
        if (file(buildSrcModuleFile).exists()) {
            val hasBuildSrc = projectModuleManager
                .select("modules")?.first()
                ?.select("module[filepath*=buildSrc]")?.isNotEmpty() ?: false

            if (!hasBuildSrc) {
                projectModuleManager
                    .select("modules").first()
                    .appendElement("module")
                    .attr("fileurl", "file://\$PROJECT_DIR\$/$buildSrcModuleFile")
                    .attr("filepath", "\$PROJECT_DIR\$/$buildSrcModuleFile")
            }
        }
    }

    private
    fun configureFrameworkDetectionExcludes(root: Element) {
        val componentName = "FrameworkDetectionExcludesConfiguration"
        root.removeBySelector("component[name=$componentName]")
            .appendElement("component").attr("name", componentName)
            .appendElement("type").attr("id", "android")
            .appendElement("type").attr("id", "web")
    }

    private
    fun configureCopyright(root: Element) {
        val options = mapOf(
            "notice" to "Copyright ${'$'}{today.year} the original author or authors.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;     http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.",
            "keyword" to "Copyright",
            "allowReplaceKeyword" to "",
            "myName" to "ASL2",
            "myLocal" to "true")
        val copyrightManager = root.select("component[name=CopyrightManager]").first()
        copyrightManager.attr("default", "ASL2")
        copyrightManager.createOrEmptyOutChildElement("copyright").let {
            options.forEach { name, value ->
                it.attr(name, value)
            }
        }
    }

    private
    fun configureCompilerConfiguration(root: Element) {
        val compilerConfiguration = root.select("component[name=CompilerConfiguration]").first()
        compilerConfiguration.createOrEmptyOutChildElement("excludeFromCompile")
        compilerConfiguration.removeBySelector("option[name=BUILD_PROCESS_HEAP_SIZE]")
            .appendElement("option")
            .attr("name", "BUILD_PROCESS_HEAP_SIZE")
            .attr("value", "2048")
    }

    private
    fun getGradleRunnerConfiguration(runnerName: String, commandLine: String): String {
        return """
            <configuration default="false" name="$runnerName" type="Application" factoryName="Application">
                <extension name="coverage" enabled="false" merge="false" />
                <option name="MAIN_CLASS_NAME" value="org.gradle.testing.internal.util.GradlewRunner" />
                <option name="VM_PARAMETERS" value="" />
                <option name="PROGRAM_PARAMETERS" value="$commandLine" />
                <option name="WORKING_DIRECTORY" value="file://${'$'}PROJECT_DIR$" />
                <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="false" />
                <option name="ALTERNATIVE_JRE_PATH" value="" />
                <option name="ENABLE_SWING_INSPECTOR" value="false" />
                <option name="ENV_VARIABLES" />
                <option name="PASS_PARENT_ENVS" value="true" />
                <module name="internalTesting" />
                <envs>
                    <env name="TERM" value="xterm" />
                </envs>
                <method>
                    <option name="Make" enabled="false" />
                </method>
            </configuration>"""
    }

    @Suppress("UNCHECKED_CAST")
    private fun getDefaultJunitVmParameter(docsProject: Project): String {
        val rootProject = docsProject.rootProject
        val releaseNotesMarkdown: PegDown by docsProject.tasks
        val releaseNotes: Copy by docsProject.tasks
        val vmParameter = mutableListOf(
            "-ea",
            "-Dorg.gradle.docs.releasenotes.source=${releaseNotesMarkdown.markdownFile}",
            "-Dorg.gradle.docs.releasenotes.rendered=${releaseNotes.destinationDir.resolve(releaseNotes.property("fileName") as String)}",
            "-DintegTest.gradleHomeDir=\$MODULE_DIR\$/build/integ test",
            "-DintegTest.gradleUserHomeDir=${rootProject.file("intTestHomeDir").absolutePath}",
            "-DintegTest.libsRepo=${rootProject.file("build/repo").absolutePath}",
            "-Dorg.gradle.integtest.daemon.registry=${rootProject.file("build/daemon").absolutePath}",
            "-DintegTest.distsDir=${rootProject.base.distsDir.absolutePath}",
            "-Dorg.gradle.public.api.includes=${(rootProject.property("publicApiIncludes") as List<String>).joinToString(":")}",
            "-Dorg.gradle.public.api.excludes=${(rootProject.property("publicApiExcludes") as List<String>).joinToString(":")}",
            "-Dorg.gradle.integtest.executer=embedded",
            "-Dorg.gradle.integtest.versions=latest",
            "-Dorg.gradle.integtest.native.toolChains=default",
            "-Dorg.gradle.integtest.multiversion=default",
            "-Dorg.gradle.integtest.testkit.compatibility=current",
            "-Xmx512m"
        )

        if (!BuildEnvironment.javaVersion.isJava8Compatible) {
            vmParameter.add("-XX:MaxPermSize=512m")
        }
        return vmParameter.joinToString(" ") {
            if (it.contains(" ")) "\"$it\""
            else it
        }
    }
}


private
val Project.rootExcludeDirs
    get() = setOf<File>(
        file("intTestHomeDir"),
        file("buildSrc/build"),
        file("buildSrc/.gradle"))


private
const val GRADLE_CONFIGURATION = """
    <configuration default="false" name="Gradle" type="Application" factoryName="Application">
       <extension name="coverage" enabled="false" merge="false" />
       <option name="MAIN_CLASS_NAME" value="org.gradle.debug.GradleRunConfiguration" />
       <option name="VM_PARAMETERS" value="" />
       <option name="PROGRAM_PARAMETERS" value="" />
       <option name="WORKING_DIRECTORY" value="file://${'$'}PROJECT_DIR$" />
       <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="false" />
       <option name="ALTERNATIVE_JRE_PATH" value="" />
       <option name="ENABLE_SWING_INSPECTOR" value="false" />
       <option name="ENV_VARIABLES" />
       <option name="PASS_PARENT_ENVS" value="true" />
       <module name="integTest" />
       <envs />
       <RunnerSettings RunnerId="Debug">
         <option name="DEBUG_PORT" value="63810" />
         <option name="TRANSPORT" value="0" />
         <option name="LOCAL" value="true" />
       </RunnerSettings>
       <RunnerSettings RunnerId="Run" />
       <ConfigurationWrapper RunnerId="Debug" />
       <ConfigurationWrapper RunnerId="Run" />
       <method />
    </configuration>
"""


private
const val CODE_STYLE_SETTINGS = """
    <component name="ProjectCodeStyleSettingsManager">
        <option name="PER_PROJECT_SETTINGS">
            <value>
                <option name="USE_SAME_INDENTS" value="true" />
                <option name="CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND" value="999" />
                <option name="RIGHT_MARGIN" value="200" />
                <option name="JD_ALIGN_PARAM_COMMENTS" value="false" />
                <option name="JD_ALIGN_EXCEPTION_COMMENTS" value="false" />
                <option name="JD_P_AT_EMPTY_LINES" value="false" />
                <option name="JD_KEEP_EMPTY_PARAMETER" value="false" />
                <option name="JD_KEEP_EMPTY_EXCEPTION" value="false" />
                <option name="JD_KEEP_EMPTY_RETURN" value="false" />
                <option name="WRAP_COMMENTS" value="true" />
                <option name="IF_BRACE_FORCE" value="3" />
                <option name="DOWHILE_BRACE_FORCE" value="3" />
                <option name="WHILE_BRACE_FORCE" value="3" />
                <option name="FOR_BRACE_FORCE" value="3" />
                <codeStyleSettings language="JAVA">
                    <option name="KEEP_CONTROL_STATEMENT_IN_ONE_LINE" value="false" />
                    <option name="IF_BRACE_FORCE" value="3" />
                    <option name="DOWHILE_BRACE_FORCE" value="3" />
                    <option name="WHILE_BRACE_FORCE" value="3" />
                    <option name="FOR_BRACE_FORCE" value="3" />
                </codeStyleSettings>
                <GroovyCodeStyleSettings>
                    <option name="ALIGN_NAMED_ARGS_IN_MAP" value="false" />
                    <option name="CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND" value="999" />
                </GroovyCodeStyleSettings>
            </value>
        </option>
        <option name="USE_PER_PROJECT_SETTINGS" value="true" />
    </component>
"""


private
const val GROOVY_COMPILER_SETTINGS = """
    <component name="GroovyCompilerProjectConfiguration">
        <excludes>
            <file url="file://${'$'}PROJECT_DIR$/subprojects/plugins/src/test/groovy/org/gradle/api/internal/tasks/testing/junit/JUnitTestClassProcessorTest.groovy" />
        </excludes>
        <option name="heapSize" value="2000" />
    </component>
"""


private
fun XmlProvider.withJsoup(function: (Document) -> Unit) {
    val xml = asString()
    val document = Jsoup.parse(xml.toString(), "", Parser.xmlParser())
    function(document)
    document.outputSettings().escapeMode(Entities.EscapeMode.xhtml)
    xml.replace(0, xml.length, document.toString())
}


private
fun Element.createOrEmptyOutChildElement(childName: String): Element {
    val children = getElementsByTag(childName)
    if (children.isEmpty()) {
        return appendElement(childName)
    }
    return children.first().apply {
        children().remove()
    }
}


private
fun Element.removeBySelector(selector: String): Element =
    apply { select(selector).remove() }
