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

import org.gradle.internal.Factory


/**
 * Android Gradle Plugin Versions.
 *
 * If you need to iterate locally on changes to AGP sources:
 * - hardcode latest nightly to `4.0.0-dev`
 * - change the repository url in `AGP_NIGHTLY_REPOSITORY_DECLARATION` to `file:///path/to/agp-src/out/repo`
 * - run `./gradlew :publishAndroidGradleLocal` in `/path/to/agp-src/tools`
 */
class AndroidGradlePluginVersions {

    public static final String OVERRIDE_VERSION_CHECK = '-Dcom.android.build.gradle.overrideVersionCheck=true'

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

    static boolean isAgpNightly(String agpVersion) {
        return agpVersion.contains("-") && agpVersion.substring(agpVersion.indexOf("-") + 1).matches("^[0-9].*")
    }

    static File createAgpNightlyRepositoryInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        mirrors << AGP_NIGHTLY_REPOSITORY_INIT_SCRIPT
        return mirrors
    }

    private final Factory<Properties> propertiesFactory
    private Properties properties

    AndroidGradlePluginVersions() {
        this(new ClasspathVersionSource("agp-versions.properties", AndroidGradlePluginVersions.classLoader))
    }

    private AndroidGradlePluginVersions(Factory<Properties> propertiesFactory) {
        this.propertiesFactory = propertiesFactory
    }

    List<String> getLatests() {
        return getVersionList("latests")
    }

    String getNightly() {
        return getVersion("nightly")
    }

    List<String> getLatestsPlusNightly() {
        return [latests, [nightly]].flatten() as List<String>
    }

    List<String> getLatestsFromMinor(String lowerBound) {
        assert lowerBound.matches("^[0-9]+\\.[0-9]+\$")
        def withBound = (latests + lowerBound).sort()
        return withBound.subList(withBound.indexOf(lowerBound) + 1, withBound.size())
    }

    String getLatestOfMinor(String lowerBound) {
        return getLatestsFromMinor(lowerBound).first()
    }

    List<String> getLatestsFromMinorPlusNightly(String lowerBound) {
        return [getLatestsFromMinor(lowerBound), [nightly]].flatten() as List<String>
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
