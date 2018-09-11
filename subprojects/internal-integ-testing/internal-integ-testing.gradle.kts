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
import org.gradle.build.ReproduciblePropertiesWriter
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import java.util.Properties

dependencies {
    compile(library("groovy"))
    compile(project(":internalTesting"))
    compile(project(":cli"))
    compile(project(":launcher"))
    compile(project(":native"))
    compile(testLibrary("jetty"))
    compile("org.littleshoot:littleproxy:1.1.0-beta1")
    compile(library("gcs"))
    compile(library("commons_httpclient"))
    compile(library("joda"))
    compile(library("jackson_core"))
    compile(library("jackson_annotations"))
    compile(library("jackson_databind"))
    compile(library("ivy"))
    compile(gradle5Platform(testLibrary("sshd")))
    compile(library("gson"))
    compile(library("joda"))
    compile(library("jsch"))
    compile(library("jcifs"))
    compile(library("jansi"))
    compile(library("commons_collections"))
    compile("org.apache.mina:mina-core")
    compile(testLibrary("sampleCheck"))

    implementation(project(":dependencyManagement"))

    runtime(project(":logging"))
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

testFixtures {
    from(":core", "main")
}

val generatedResourcesDir = gradlebuildJava.generatedResourcesDir

val prepareVersionsInfo = tasks.register<PrepareVersionsInfo>("prepareVersionsInfo") {
    destFile = generatedResourcesDir.resolve("all-released-versions.properties")
    versions = releasedVersions.allVersions
    mostRecent = releasedVersions.mostRecentRelease
    mostRecentSnapshot = releasedVersions.mostRecentSnapshot
}

sourceSets["main"].output.dir(mapOf("builtBy" to prepareVersionsInfo), generatedResourcesDir)

ideConfiguration {
    makeAllSourceDirsTestSourceDirsToWorkaroundIssuesWithIDEA13()
}


@CacheableTask
open class PrepareVersionsInfo : DefaultTask() {

    @OutputFile
    lateinit var destFile: File

    @Input
    lateinit var mostRecent: String

    @Input
    lateinit var versions: List<String>

    @Input
    lateinit var mostRecentSnapshot: String

    @TaskAction
    fun prepareVersions() {
        val properties = Properties()
        properties["mostRecent"] = mostRecent
        properties["mostRecentSnapshot"] = mostRecentSnapshot
        properties["versions"] = versions.joinToString(" ")
        ReproduciblePropertiesWriter.store(properties, destFile)
    }
}
