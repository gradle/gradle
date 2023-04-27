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

package gradlebuild.buildutils.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.util.PropertiesUtils
import org.gradle.util.internal.VersionNumber
import org.gradle.work.DisableCachingByDefault
import java.util.Properties


@DisableCachingByDefault(because = "Not worth caching")
abstract class UpdateInitPluginTemplateVersionFile : DefaultTask() {

    private
    val devSuffixes = arrayOf(
        "-SNAP\\d+",
        "-SNAPSHOT",
        "-alpha-?\\d+",
        "-beta-?\\d+",
        "-dev-?\\d+",
        "-dev-\\d+-\\d+",
        "-rc-?\\d+",
        "-RC-?\\d+",
        "-M.+",
        "-eap-?\\d+"
    )

    @get:Internal
    abstract val libraryVersionFile: RegularFileProperty

    @TaskAction
    fun updateInitPluginTemplateVersionFile() {
        val versionProperties = Properties()

        findLatest("scala-library", "org.scala-lang:scala-library:2.13.+", versionProperties)
        val scalaVersion = VersionNumber.parse(versionProperties["scala-library"] as String)
        versionProperties["scala"] = "${scalaVersion.major}.${scalaVersion.minor}"

        // The latest released version is 2.0.0-M1, which is excluded by "don't use snapshot" strategy
        findLatest("scala-xml", "org.scala-lang.modules:scala-xml_${versionProperties["scala"]}:1.2.0", versionProperties)
        findLatest("groovy", "org.codehaus.groovy:groovy:[3.0,4.0)", versionProperties)
        findLatest("junit", "junit:junit:(4.0,)", versionProperties)
        findLatest("junit-jupiter", "org.junit.jupiter:junit-jupiter-api:(5,)", versionProperties)
        findLatest("testng", "org.testng:testng:(6.0,7.6.0)", versionProperties) // TestNG 7.6.0 and above require JDK 11; see https://groups.google.com/g/testng-users/c/BAFB1vk-kok
        findLatest("slf4j", "org.slf4j:slf4j-api:(1.7,)", versionProperties)

        // Starting with ScalaTest 3.1.0, the third party integration were moved out of the main JAR
        findLatest("scalatest", "org.scalatest:scalatest_${versionProperties["scala"]}:(3.0,)", versionProperties)
        findLatest("scalatestplus-junit", "org.scalatestplus:junit-4-12_${versionProperties["scala"]}:(3.1,)", versionProperties)

        val groovyVersion = VersionNumber.parse(versionProperties["groovy"] as String)
        versionProperties["spock"] = "2.2-groovy-${groovyVersion.major}.${groovyVersion.minor}"

        findLatest("guava", "com.google.guava:guava:(20,)", versionProperties)
        findLatest("commons-math", "org.apache.commons:commons-math3:latest.release", versionProperties)
        findLatest("commons-text", "org.apache.commons:commons-text:latest.release", versionProperties)
        findLatest("kotlin", "org.jetbrains.kotlin:kotlin-gradle-plugin:(1.4,)", versionProperties)

        store(versionProperties)
    }

    private
    fun store(properties: Properties) {
        PropertiesUtils.store(
            properties, libraryVersionFile.get().asFile,
            "Generated file, please do not edit - Version values used in build-init templates",
            Charsets.ISO_8859_1, "\n"
        )
    }

    private
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
}
