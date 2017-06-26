/*
 * Copyright 2012 the original author or authors.
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

package org.gradle

import groovy.io.FileType
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Shared

import static org.hamcrest.Matchers.containsString

class AllDistributionIntegrationSpec extends DistributionIntegrationSpec {

    @Shared String version = buildContext.distZipVersion.version

    @Override
    String getDistributionLabel() {
        "all"
    }

    def allZipContents() {
        given:
        TestFile contentsDir = unpackDistribution()

        expect:
        checkMinimalContents(contentsDir)

        // Source
        contentsDir.file('src').eachFile { TestFile file -> file.assertIsDir() }
        contentsDir.file('src/core/org/gradle/api/Project.java').assertIsFile()
        contentsDir.file('src/wrapper/org/gradle/wrapper/WrapperExecutor.java').assertIsFile()

        // Samples
        contentsDir.file('samples/java/quickstart/build.gradle').assertIsFile()

        def buildAndGradleDirs = []
        contentsDir.file('samples').eachFileRecurse(FileType.DIRECTORIES) {
            if (it.name == "build" || it.name == ".gradle") {
                buildAndGradleDirs << it
            }
        }
        buildAndGradleDirs == []

        // Javadoc
        contentsDir.file('docs/javadoc/index.html').assertIsFile()
        contentsDir.file('docs/javadoc/index.html').assertContents(containsString("Gradle API ${version}"))
        contentsDir.file('docs/javadoc/org/gradle/api/Project.html').assertIsFile()

        // Userguide
        contentsDir.file('docs/userguide/userguide.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide.html').assertContents(containsString("<h3 class=\"releaseinfo\">Version ${version}</h3>"))
        contentsDir.file('docs/userguide/userguide_single.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide_single.html').assertContents(containsString("<h3 class=\"releaseinfo\">Version ${version}</h3>"))
//        contentsDir.file('docs/userguide/userguide.pdf').assertIsFile()

        // DSL reference
        contentsDir.file('docs/dsl/index.html').assertIsFile()
        contentsDir.file('docs/dsl/index.html').assertContents(containsString("<title>Gradle DSL Version ${version}</title>"))
    }

}
