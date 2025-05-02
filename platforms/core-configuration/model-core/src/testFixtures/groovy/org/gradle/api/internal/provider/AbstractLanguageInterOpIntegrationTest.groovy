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

package org.gradle.api.internal.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

class AbstractLanguageInterOpIntegrationTest extends AbstractIntegrationSpec {
    TestFile pluginDir = file("buildSrc/plugin")

    void usesKotlin(TestFile dir) {
        def buildfile = dir.file("build.gradle.kts")
        if (!buildfile.file) {
            buildfile.createFile()
        }
        buildfile.text = KotlinDslTestUtil.kotlinDslBuildSrcScript + buildfile.text
    }

    def setup() {
        executer.withRepositoryMirrors()
        file("buildSrc/settings.gradle.kts") << """
            include("plugin")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                implementation(project(":plugin"))
            }
        """
    }

    def cleanup() {
        if (!failed) {
            return
        }

        // Let's copy the Kotlin compiler logs in case of failure
        def today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        Pattern pattern = ~/kotlin-daemon\.${today}\..*\.log/

        def target = buildContext.gradleUserHomeDir.createDir("kotlin-compiler-daemon").toPath()

        Files.walk(Paths.get(System.getenv("TMPDIR")))
            .filter { Files.isRegularFile(it) }
            .filter { path -> path.fileName.toString() =~ pattern }
            .map { it.toString() }
            .forEach {
                def source = Paths.get(it)
                Files.copy(source, target.resolve(source.fileName), StandardCopyOption.REPLACE_EXISTING)
            }
    }
}
