/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.gradlebuild.ProjectGroups.pluginProjects
import org.gradle.gradlebuild.ProjectGroups.implementationPluginProjects
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import java.util.concurrent.Callable

plugins {
    `java-library`
}

configurations {
    register("reports")
}

tasks.classpathManifest {
    optionalProjects = listOf("gradle-kotlin-dsl")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":cli"))
    implementation(project(":buildOption"))
    implementation(project(":native"))
    implementation(project(":modelCore"))
    implementation(project(":persistentCache"))
    implementation(project(":buildCache"))
    implementation(project(":buildCachePackaging"))
    implementation(project(":coreApi"))
    implementation(project(":files"))
    implementation(project(":processServices"))
    implementation(project(":jvmServices"))
    implementation(project(":modelGroovy"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":workerProcesses"))

    implementation(library("groovy"))
    implementation(library("ant"))
    implementation(library("guava"))
    implementation(library("inject"))
    implementation(library("asm"))
    implementation(library("asm_commons"))
    implementation(library("slf4j_api"))
    implementation(library("commons_io"))
    implementation(library("commons_lang"))
    implementation(library("nativePlatform"))
    implementation(library("commons_compress"))
    implementation(library("xmlApis"))

    runtimeOnly(project(":instantExecution"))
    runtimeOnly(project(":docs"))

    testImplementation(project(":plugins"))
    testImplementation(project(":testingBase"))
    testImplementation(project(":platformNative"))
    testImplementation(testLibrary("jsoup"))
    testImplementation(library("log4j_to_slf4j"))
    testImplementation(library("jcl_to_slf4j"))

    testRuntimeOnly(library("xerces"))
    testRuntimeOnly(project(":diagnostics"))
    testRuntimeOnly(project(":compositeBuilds"))

    testFixturesImplementation(project(":internalTesting"))
    testFixturesImplementation(library("ivy"))

    testFixturesRuntimeOnly(project(":runtimeApiInfo"))
    val allCoreRuntimeExtensions: DependencySet by rootProject.extra
    allCoreRuntimeExtensions.forEach {
        testFixturesRuntimeOnly(it)
    }
    testFixturesRuntimeOnly(project(":testingJunitPlatform"))

    testImplementation(project(":dependencyManagement"))

    integTestImplementation(project(":workers"))
    integTestImplementation(project(":dependencyManagement"))
    integTestImplementation(project(":launcherStartup"))
    integTestImplementation(project(":plugins"))
    integTestImplementation(library("jansi"))
    integTestImplementation(library("jetbrains_annotations"))
    integTestImplementation(testLibrary("jetty"))
    integTestImplementation(testLibrary("littleproxy"))

    integTestRuntimeOnly(project(":maven"))
    integTestRuntimeOnly(project(":apiMetadata"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}

testFixtures {
    from(":coreApi")
    from(":messaging")
    from(":modelCore")
    from(":logging")
    from(":baseServices")
    from(":diagnostics")
}

tasks.test {
    setForkEvery(200)
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

listOf(tasks.compileGroovy, tasks.compileTestGroovy).forEach {
    it { groovyOptions.fork("memoryInitialSize" to "128M", "memoryMaximumSize" to "1G") }
}

val pluginsManifest by tasks.registering(WriteProperties::class) {
    property("plugins", Callable {
        pluginProjects.map { it.base.archivesBaseName }.sorted().joinToString(",")
    })
    outputFile = File(generatedResourcesDir, "gradle-plugins.properties")
}

sourceSets.main {
    output.dir(generatedResourcesDir, "builtBy" to pluginsManifest)
}

val implementationPluginsManifest by tasks.registering(WriteProperties::class) {
    property("plugins", Callable {
        implementationPluginProjects.map { it.base.archivesBaseName }.sorted().joinToString(",")
    })
    outputFile = File(generatedResourcesDir, "gradle-implementation-plugins.properties")
}

sourceSets.main {
    output.dir(generatedResourcesDir, "builtBy" to implementationPluginsManifest)
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
