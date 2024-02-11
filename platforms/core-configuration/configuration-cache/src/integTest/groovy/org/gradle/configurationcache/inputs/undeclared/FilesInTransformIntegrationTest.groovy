/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared


import org.gradle.configurationcache.fixtures.TransformFixture
import org.gradle.configurationcache.inputs.process.AbstractProcessIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class FilesInTransformIntegrationTest extends AbstractProcessIntegrationTest {
    def setup() {
        inputFile.text = "INPUT FILE CONTENT"
    }

    def "reading a file in transform action with #task does not create a build input"() {
        given:
        getTransformFixture().tap {
            withTransformPlugin(testDirectory.createDir("buildSrc"))
            withJavaLibrarySubproject(testDirectory.createDir("subproject"))
        }

        settingsFile("""
            include("subproject")
        """)

        buildFile("""
            plugins { id("${TransformFixture.TRANSFORM_PLUGIN_ID}") }
            ${mavenCentralRepository()}
            dependencies {
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} "org.apache.commons:commons-math3:3.6.1"
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} project(":subproject")
            }
        """)

        when:
        configurationCacheRun(":$task")

        then:
        outputContains("INPUT FILE CONTENT")
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }

        where:
        task << ["resolveCollection", "resolveProviders"]
    }

    def "reading a file in transform action with #task of buildSrc build does not create a build input"() {
        given:
        createDir("buildSrc") {
            getTransformFixture().tap {
                withTransformPlugin(dir("transform-plugin"))
                withJavaLibrarySubproject(dir("subproject"))
            }

            file("settings.gradle") << """
                pluginManagement { includeBuild("transform-plugin") }
                include("subproject")
            """

            file("build.gradle") << """
            plugins { id("${TransformFixture.TRANSFORM_PLUGIN_ID}") }
            ${mavenCentralRepository()}
            dependencies {
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} "org.apache.commons:commons-math3:3.6.1"
                ${TransformFixture.TRANSFORM_SOURCE_CONFIGURATION} project(":subproject")
            }

            tasks.named("classes") {
                dependsOn("$task")
            }
            """
        }

        buildFile("""
        tasks.register("check") {}
        """)

        when:
        configurationCacheRun(":check")

        then:
        outputContains("INPUT FILE CONTENT")
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }

        where:
        task << ["resolveCollection", "resolveProviders"]
    }

    private TransformFixture getTransformFixture() {
        return new TransformFixture(
            """
            import ${BufferedReader.name};
            import ${FileInputStream.name};
            import ${InputStreamReader.name};
            """,

            "",

            """
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("${TextUtil.escapeString(inputFile.absolutePath)}")))) {
                String line = in.readLine();
                while (line != null) {
                    System.out.println(line);
                    line = in.readLine();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            """
        )
    }

    private TestFile getInputFile() {
        return testDirectory.file("test-input.txt")
    }
}
