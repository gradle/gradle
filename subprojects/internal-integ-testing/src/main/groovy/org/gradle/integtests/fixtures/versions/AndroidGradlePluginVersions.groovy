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

package org.gradle.integtests.fixtures.versions

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile


/**
 * Android Gradle Plugin Versions.
 *
 * If you need to iterate locally on changes to AGP sources:
 * - hardcode latest nightly to `4.0.0-dev`
 * - change the repository url in `AGP_NIGHTLY_REPOSITORY_DECLARATION` to `file:///path/to/agp-src/out/repo`
 * - run `./gradlew :publishAndroidGradleLocal` in `/path/to/agp-src/tools`
 */
class AndroidGradlePluginVersions {

    private static final String AGP_NIGHTLY_REPOSITORY_DECLARATION = '''
        maven {
            name = 'agp-nightlies'
            url = 'https://repo.gradle.org/gradle/ext-snapshots-local/'
        }
    '''

    private static final String AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT = """
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

    static void usingAgpVersion(GradleExecuter executer, TestFile buildFile, String agpVersion) {
        println "> Using AGP version ${agpVersion}"
        buildFile.text = replaceAgpVersion(buildFile.text, agpVersion)
        executer.beforeExecute {
            withArgument("-Pandroid.overrideVersionCheck=true")
        }
        if (isNightly(agpVersion)) {
            usingAgpNightlyRepository(executer)
        }
    }

    private static boolean isNightly(String agpVersion) {
        return agpVersion.contains("-") && agpVersion.substring(agpVersion.indexOf("-") + 1).matches("^[0-9].*")
    }

    static void usingAgpNightlyRepository(GradleExecuter executer) {
        def init = createAgpNightlyRepositoryInitScript()
        executer.beforeExecute {
            usingInitScript(init)
        }
    }

    private static File createAgpNightlyRepositoryInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        mirrors << AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT
        return mirrors
    }


    private static String replaceAgpVersion(String scriptText, String agpVersion) {
        return scriptText.replaceAll(
            "(['\"]com.android.tools.build:gradle:).+(['\"])",
            "${'$'}1$agpVersion${'$'}2"
        )
    }

    private final Factory<Properties> propertiesFactory
    private Properties properties

    AndroidGradlePluginVersions() {
        this(new ClasspathVersionSource("agp-versions.properties", AndroidGradlePluginVersions.classLoader))
    }

    private AndroidGradlePluginVersions(Factory<Properties> propertiesFactory) {
        this.propertiesFactory = propertiesFactory
    }

    List<String> getLatestAgpVersions() {
        return getVersionList("latests")
    }

    List<String> getLatestAgpVersionsFromMinor(String lowerBound) {
        assert lowerBound.matches("^[0-9]+\\.[0-9]+\$")
        def latests = getLatestAgpVersions()
        def withBound = (latests + lowerBound).sort()
        return withBound.subList(withBound.indexOf(lowerBound) + 1, withBound.size())
    }

    String getLatestNightly() {
        return getVersion("nightly")
    }

    private List<String> getVersionList(String name) {
        return loadedProperties().getProperty(name).split(",")
    }

    private String getVersion(String name) {
        return loadedProperties().getProperty(name)
    }

    private Properties loadedProperties() {
        if (properties == null) {
            properties = propertiesFactory.create()
        }
        return properties
    }
}
