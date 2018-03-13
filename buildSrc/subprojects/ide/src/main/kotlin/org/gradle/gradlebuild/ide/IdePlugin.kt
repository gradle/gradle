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

import accessors.base
import accessors.eclipse
import accessors.idea
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.ProjectGroups.projectsRequiringJava8
import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.docs.PegDown
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModule
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser
import java.io.File


private
const val ideConfigurationBaseName = "ideConfiguration"


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
                            configureCopyright(projectElement)

                            // We are using an extension method instead of appending a fixed XML String,
                            // since jsoup `append` method converts all xml tags to lower case.
                            // In doing so, tags in the code style settings are ignored.
                            projectElement.removeBySelector("component[name=ProjectCodeStyleConfiguration]")
                                .configureCodeStyleSettings()

                            configureFrameworkDetectionExcludes(projectElement)
                            configureBuildSrc(projectElement)
                        }
                        // TODO replace this hack by trying out with kotlinx.dom
                        val xmlStringBuilder = asString()
                        val toReplace = "{newline}"
                        var startIndex = xmlStringBuilder.indexOf(toReplace)
                        while (startIndex > -1) {
                            xmlStringBuilder.replace(startIndex, startIndex + toReplace.length, "&#10;")
                            startIndex = xmlStringBuilder.indexOf(toReplace)
                        }
                    }
                }
                workspace {
                    iws {
                        withXml {
                            withJsoup { document ->
                                val projectElement = document.getElementsByTag("project").first()
                                projectElement.createOrEmptyOutChildElement("CompilerWorkspaceConfiguration")
                                    .option("COMPILER_PROCESS_HEAP_SIZE", "2048")
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
            // TODO Get rid of {newline} and the replacement hack by using a better XML parser / writer
            "notice" to "Copyright ${'$'}{today.year} the original author or authors.{newline}{newline}Licensed under the Apache License, Version 2.0 (the \"License\");{newline}you may not use this file except in compliance with the License.{newline}You may obtain a copy of the License at{newline}{newline}     http://www.apache.org/licenses/LICENSE-2.0{newline}{newline}Unless required by applicable law or agreed to in writing, software{newline}distributed under the License is distributed on an \"AS IS\" BASIS,{newline}WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.{newline}See the License for the specific language governing permissions and{newline}limitations under the License.",
            "keyword" to "Copyright",
            "allowReplaceKeyword" to "",
            "myName" to "ASL2",
            "myLocal" to "true")
        val copyrightManager = root.select("component[name=CopyrightManager]").first()
        copyrightManager.attr("default", "ASL2")
        copyrightManager.createOrEmptyOutChildElement("copyright").let {
            options.forEach { name, value ->
                it.option(name, value)
            }
        }
    }

    private
    fun configureCompilerConfiguration(root: Element) {
        val compilerConfiguration = root.select("component[name=CompilerConfiguration]").first()
        compilerConfiguration.createOrEmptyOutChildElement("excludeFromCompile")
        compilerConfiguration.removeBySelector("option[name=BUILD_PROCESS_HEAP_SIZE]")
            .option("BUILD_PROCESS_HEAP_SIZE", "2048")
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
    private
    fun getDefaultJunitVmParameter(docsProject: Project): String {
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
            "-Dorg.gradle.public.api.includes=${PublicApi.includes.joinToString(":")}",
            "-Dorg.gradle.public.api.excludes=${PublicApi.excludes.joinToString(":")}",
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
fun Element.configureCodeStyleSettings() {
    val config = appendElement("component")
        .attr("name", "ProjectCodeStyleConfiguration")

    config.option("USE_PER_PROJECT_SETTINGS", "true")
    val codeScheme = config.appendElement("code_scheme")
        .attr("name", "Project")
        .attr("version", "173")

    listOf(
        "USE_SAME_INDENTS" to "true",
        "IGNORE_SAME_INDENTS_FOR_LANGUAGES" to "true",
        "RIGHT_MARGIN" to "200",
        "WRAP_COMMENTS" to "true",
        "IF_BRACE_FORCE" to "3",
        "DOWHILE_BRACE_FORCE" to "3",
        "WHILE_BRACE_FORCE" to "3",
        "FOR_BRACE_FORCE" to "3"
    ).forEach { (name, value) ->
        codeScheme.option(name, value)
    }

    val groovyCodeStyleSettings = codeScheme.appendElement("GroovyCodeStyleSettings")

    listOf(
        "CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND" to "999",
        "NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND" to "999",
        "ALIGN_NAMED_ARGS_IN_MAP" to "false"
    ).forEach { (name, value) ->
        groovyCodeStyleSettings.option(name, value)
    }

    val javaCodeStyleSettings = codeScheme.appendElement("JavaCodeStyleSettings")

    listOf(
        "CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND" to "999",
        "NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND" to "999",
        "JD_ALIGN_PARAM_COMMENTS" to "false",
        "JD_ALIGN_EXCEPTION_COMMENTS" to "false",
        "JD_P_AT_EMPTY_LINES" to "false",
        "JD_KEEP_EMPTY_PARAMETER" to "false",
        "JD_KEEP_EMPTY_EXCEPTION" to "false",
        "JD_KEEP_EMPTY_RETURN" to "false"
    ).forEach { (name, value) ->
        javaCodeStyleSettings.option(name, value)
    }
}


private
fun Element.option(name: String, value: String) {
    appendElement("option")
        .attr("name", name)
        .attr("value", value)
}


private
fun XmlProvider.withJsoup(function: (Document) -> Unit) {
    val xml = asString()
    val document = modifyXmlDocument(xml, function)
    xml.replace(0, xml.length, document)
}


private
fun modifyXmlDocument(xml: StringBuilder, function: (Document) -> Unit): String {
    val document = Jsoup.parse(xml.toString(), "", Parser.xmlParser())
    function(document)
    document.outputSettings().escapeMode(Entities.EscapeMode.xhtml)
    return document.toString()
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
