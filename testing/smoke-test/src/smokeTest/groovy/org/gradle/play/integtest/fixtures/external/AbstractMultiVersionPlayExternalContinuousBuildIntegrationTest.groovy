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

package org.gradle.play.integtest.fixtures.external

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.play.integtest.fixtures.PlayCoverage
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

@TargetCoverage({ PlayCoverage.DEFAULT })
@MultiVersionTest
abstract class AbstractMultiVersionPlayExternalContinuousBuildIntegrationTest extends AbstractPlayExternalContinuousBuildIntegrationTest {
    static def version

    static VersionNumber getVersionNumber() {
        VersionNumber.parse(version.toString())
    }

    def setup() {
        buildFile << playPlatformConfiguration(version.toString())
        executer.expectDeprecationWarning("The ProjectLayout.configurableFiles() method has been deprecated. This is scheduled to be removed in Gradle 7.0. Please use the ObjectFactory.fileCollection() method instead.")

        // Play Framework supports up to Java 11.
        Assume.assumeTrue(JavaVersion.current() <= JavaVersion.VERSION_11)
    }

    private static String playPlatformConfiguration(String version) {
        return """
        allprojects {
            play {
                platform {
                    playVersion = "${version}"
                }
            }
        }
        """
    }

}
