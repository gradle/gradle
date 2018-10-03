/*
 * Copyright 2016 the original author or authors.
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
import groovy.json.JsonSlurper
import org.gradle.testing.performance.generator.tasks.JvmProjectGeneratorTask

fun getBuildScanPluginVersion(): Any? {
    val pluginInfo = file("$rootDir/incoming/plugin.json")
    assert(pluginInfo.exists())
    val json = JsonSlurper().parse(pluginInfo) as Map<String, Any>
    assert(json.containsKey("versionNumber"))
    return json["versionNumber"]
}

tasks.register<JvmProjectGeneratorTask>("largeJavaProjectWithBuildScanPlugin") {
    doFirst {
        templateArgs["buildScanPluginVersion"] = getBuildScanPluginVersion()
    }

    dependencyGraph.run {
        size = 200
        depth = 5
        useSnapshotVersions = false // snapshots should not have a build scan specific performance impact
    }

    buildSrcTemplate = "buildsrc-plugins"
    setProjects(250)
    sourceFiles = 100
    testSourceFiles = 50 // verbose tests are time consuming
    filesPerPackage = 25
    linesOfCodePerSourceFile = 150
    rootProjectTemplates = listOf("root")
    subProjectTemplates = listOf("project-with-source")
    templateArgs = mapOf("fullTestLogging" to true, "failedTests" to true, "projectDependencies" to true, "manyPlugins" to true, "manyScripts" to true)

    doLast {
        // generate script plugins
        val scriptPlugins = 30
        val nesting = 5
        val groupedScriptIds = ((1..scriptPlugins).groupBy { it % (scriptPlugins/nesting)}.values)
        val gradleFolder =  File(destDir, "gradle")
        gradleFolder.mkdirs()
        (1..30).forEach { scriptPluginId ->
            val nestedScriptId: Int? = groupedScriptIds.find {it.contains(scriptPluginId)}?.find { it > scriptPluginId }
            val maybeApplyNestedScript = if (nestedScriptId != null) "apply from: \'../gradle/script-plugin${nestedScriptId}.gradle'" else ""
            File(gradleFolder, "script-plugin${scriptPluginId}.gradle").writeText("""
                ${maybeApplyNestedScript}
            """)
        }
    }
}

tasks.register<JvmProjectGeneratorTask>("manyInputFilesProject") {
    doFirst {
        templateArgs["buildScanPluginVersion"] = getBuildScanPluginVersion()
    }
    dependencyGraph.run {
        size = 500
        depth = 5
        useSnapshotVersions = false // snapshots should not have a build scan specific performance impact
    }
    setProjects(200)
    sourceFiles = 300
    filesPerPackage = 25
    linesOfCodePerSourceFile = 50
    subProjectTemplates = listOf("project-with-source")
    templateArgs = mapOf("projectDependencies" to true)
}
