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
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    gradlebuild.distribution.`api-java`
}

configurations {
    register("reports")
}

tasks.classpathManifest {
    optionalProjects.add("gradle-kotlin-dsl")
    // The gradle-runtime-api-info.jar is added by a 'distributions-...' project if it is on the (integration test) runtime classpath.
    // It contains information services in ':core' need to reason about the complete Gradle distribution.
    // To allow parts of ':core' code to be instantiated in unit tests without relying on this functionality, the dependency is optional.
    optionalProjects.add("gradle-runtime-api-info")
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
    implementation(project(":fileCollections"))
    implementation(project(":processServices"))
    implementation(project(":jvmServices"))
    implementation(project(":modelGroovy"))
    implementation(project(":snapshots"))
    implementation(project(":fileWatching"))
    implementation(project(":execution"))
    implementation(project(":workerProcesses"))
    implementation(project(":normalizationJava"))

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

    testImplementation(project(":plugins"))
    testImplementation(project(":testingBase"))
    testImplementation(project(":platformNative"))
    testImplementation(testLibrary("jsoup"))
    testImplementation(library("log4j_to_slf4j"))
    testImplementation(library("jcl_to_slf4j"))

    testFixturesApi(project(":baseServices")) {
        because("test fixtures expose Action")
    }
    testFixturesApi(project(":baseServicesGroovy")) {
        because("test fixtures expose AndSpec")
    }
    testFixturesApi(project(":coreApi")) {
        because("test fixtures expose Task")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures expose Logger")
    }
    testFixturesApi(project(":modelCore")) {
        because("test fixtures expose IConventionAware")
    }
    testFixturesApi(project(":buildCache")) {
        because("test fixtures expose BuildCacheController")
    }
    testFixturesApi(project(":execution")) {
        because("test fixtures expose OutputChangeListener")
    }
    testFixturesApi(project(":native")) {
        because("test fixtures expose FileSystem")
    }
    testFixturesImplementation(project(":fileCollections"))
    testFixturesImplementation(project(":native"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(project(":processServices"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":persistentCache"))
    testFixturesImplementation(project(":snapshots"))
    testFixturesImplementation(project(":normalizationJava"))
    testFixturesImplementation(library("ivy"))
    testFixturesImplementation(library("slf4j_api"))
    testFixturesImplementation(library("guava"))
    testFixturesImplementation(library("ant"))

    testFixturesRuntimeOnly(project(":pluginUse")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":dependencyManagement")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":workers")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":compositeBuilds")) {
        because("We always need a BuildStateRegistry service implementation")
    }

    testImplementation(project(":dependencyManagement"))

    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":modelCore")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":baseServices")))
    testImplementation(testFixtures(project(":diagnostics")))

    integTestImplementation(project(":workers"))
    integTestImplementation(project(":dependencyManagement"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":plugins"))
    integTestImplementation(library("jansi"))
    integTestImplementation(library("jetbrains_annotations"))
    integTestImplementation(testLibrary("jetty"))
    integTestImplementation(testLibrary("littleproxy"))
    integTestImplementation(testFixtures(project(":native")))

    testRuntimeOnly(project(":distributionsCore")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsBasics")) {
        because("Some tests utilise the 'java-gradle-plugin' and with that TestKit")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributionsCore"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

tasks.test {
    setForkEvery(200)
}
listOf(tasks.compileGroovy, tasks.compileTestGroovy).forEach {
    it { groovyOptions.fork("memoryInitialSize" to "128M", "memoryMaximumSize" to "1G") }
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
