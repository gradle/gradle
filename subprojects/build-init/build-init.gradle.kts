import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.util.VersionNumber
import java.util.*

/*
 * Copyright 2013 the original author or authors.
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
plugins {
    `java-library`
    gradlebuild.classycle
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":fileCollections"))
    implementation(project(":dependencyManagement"))
    implementation(project(":platformBase"))
    implementation(project(":platformNative"))
    implementation(project(":plugins"))
    implementation(project(":wrapper"))

    implementation(library("groovy"))
    implementation(library("slf4j_api"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("inject"))
    implementation("org.codehaus.plexus:plexus-container-default")
    implementation("org.apache.maven:maven-compat")
    implementation("org.apache.maven:maven-plugin-api")

    testImplementation(project(":cli"))
    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":processServices"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platformNative")))

    testFixturesImplementation(project(":baseServices"))

    integTestImplementation(project(":native"))
    integTestImplementation(testLibrary("jetty"))

    val allTestRuntimeDependencies: DependencySet by rootProject.extra
    allTestRuntimeDependencies.forEach {
        integTestRuntimeOnly(it)
    }

    testFixturesImplementation(project(":internalTesting"))

    testRuntimeOnly(project(":runtimeApiInfo"))
}

gradlebuildJava {
    moduleType = ModuleType.CORE
}


tasks {
    register("updateInitPluginTemplateVersionFile") {
        group = "Build init"
        doLast {

            val versionProperties = Properties()

            findLatest("scala-library", "org.scala-lang:scala-library:2.13.+", versionProperties)
            val scalaVersion = VersionNumber.parse(versionProperties["scala-library"] as String)
            versionProperties["scala"] = "${scalaVersion.major}.${scalaVersion.minor}"

            // The latest released version is 2.0.0-M1, which is excluded by "don't use snapshot" strategy
            findLatest("scala-xml", "org.scala-lang.modules:scala-xml_${versionProperties["scala"]}:1.2.0", versionProperties)
            findLatest("groovy", "org.codehaus.groovy:groovy:(2.5,)", versionProperties)
            findLatest("junit", "junit:junit:(4.0,)", versionProperties)
            findLatest("junit-jupiter", "org.junit.jupiter:junit-jupiter-api:(5,)", versionProperties)
            findLatest("testng", "org.testng:testng:(6.0,)", versionProperties)
            findLatest("slf4j", "org.slf4j:slf4j-api:(1.7,)", versionProperties)

            // Starting with ScalaTest 3.1.0, the third party integration were moved out of the main JAR
            findLatest("scalatest", "org.scalatest:scalatest_${versionProperties["scala"]}:(3.0,)", versionProperties)
            findLatest("scalatestplus-junit", "org.scalatestplus:junit-4-12_${versionProperties["scala"]}:(3.1,)", versionProperties)

            val groovyVersion = VersionNumber.parse(versionProperties["groovy"] as String)
            versionProperties["spock"] = "1.3-groovy-${groovyVersion.major}.${groovyVersion.minor}"

            findLatest("guava", "com.google.guava:guava:(20,)", versionProperties)
            findLatest("commons-math", "org.apache.commons:commons-math3:latest.release", versionProperties)
            findLatest("kotlin", "org.jetbrains.kotlin:kotlin-gradle-plugin:(1.3,)", versionProperties)

            val libraryVersionFile = file("src/main/resources/org/gradle/buildinit/tasks/templates/library-versions.properties")
            org.gradle.build.ReproduciblePropertiesWriter.store(
                versionProperties,
                libraryVersionFile,
                "Generated file, please do not edit - Version values used in build-init templates"
            )
        }
    }
}

fun findLatest(name: String, notation: String, dest: Properties) {
    val libDependencies = arrayOf(project.dependencies.create(notation))
    val templateVersionConfiguration = project.configurations.detachedConfiguration(*libDependencies)
    templateVersionConfiguration.resolutionStrategy.componentSelection.all {
        devSuffixes.forEach {
            if (candidate.version.matches(".+$it\$".toRegex())) {
                reject("don't use snapshots")
                return@forEach
            }
        }
    }
    templateVersionConfiguration.isTransitive = false
    val resolutionResult: ResolutionResult = templateVersionConfiguration.incoming.resolutionResult
    val matches: List<ResolvedComponentResult> = resolutionResult.allComponents.filter { it != resolutionResult.root }
    if (matches.isEmpty()) {
        throw GradleException("Could not locate any matches for $notation")
    }
    matches.forEach { dep -> dest[name] = (dep.id as ModuleComponentIdentifier).version }
}

val devSuffixes = arrayOf(
    "-SNAP\\d+",
    "-SNAPSHOT",
    "-alpha-?\\d+",
    "-beta-?\\d+",
    "-dev-?\\d+",
    "-rc-?\\d+",
    "-RC-?\\d+",
    "-M\\d+",
    "-eap-?\\d+"
)
