/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.android.AndroidHome
import org.gradle.test.fixtures.file.TestFile


/**
 * Base Android / Instant execution integration test.
 *
 * In order to iterate quickly on changes to AGP:
 * - change `AGP_VERSION` to `3.6.0-dev`
 * - change the repository url in `AGP_NIGHTLY_REPOSITORY_DECLARATION` to `file:///path/to/agp-src/out/repo`
 * - run `./gradlew :publishAndroidGradleLocal` in `/path/to/agp-src/tools`
 */
@CompileStatic
abstract class AbstractInstantExecutionAndroidIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    static final String AGP_VERSION = "3.6.0-20190915023021+0200"

    static final String AGP_NIGHTLY_REPOSITORY_DECLARATION = '''
        maven {
            name = 'agp-nightlies'
            url = 'https://repo.gradle.org/gradle/ext-snapshots-local/'
        }
    '''

    static final String AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT = """
        allprojects {
            buildscript {
                repositories {
                    $AGP_NIGHTLY_REPOSITORY_DECLARATION
                }
            }
            repositories {
                $AGP_NIGHTLY_REPOSITORY_DECLARATION
            }
        }
    """

    def setup() {
        AndroidHome.assumeIsSet()
    }

    static String replaceAgpVersion(String scriptText, String agpVersion = AGP_VERSION) {
        return scriptText.replaceAll(
            "(['\"]com.android.tools.build:gradle:).+(['\"])",
            "${'$'}1$agpVersion${'$'}2"
        )
    }

    void withAgpNightly(String agpVersion = AGP_VERSION) {
        withAgpNightly(buildFile, agpVersion)
    }

    void withAgpNightly(TestFile buildFile, String agpVersion = AGP_VERSION) {

        println "> Using AGP nightly ${agpVersion}"

        // Inject AGP nightly repository
        def init = file("gradle/agp-nightly.init.gradle") << AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT
        executer.beforeExecute {
            withArgument("-I")
            withArgument(init.path)
        }

        // Inject AGP nightly version
        buildFile.text = replaceAgpVersion(buildFile.text, agpVersion)
    }

    void copyRemoteProject(String remoteProject) {
        new TestFile(new File("build/$remoteProject")).copyTo(testDirectory)
    }
}
