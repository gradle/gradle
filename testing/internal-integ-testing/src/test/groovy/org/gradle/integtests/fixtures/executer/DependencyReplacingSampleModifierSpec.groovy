/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.exemplar.model.Sample
import org.gradle.integtests.fixtures.executer.DependencyReplacingSampleModifier
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

class DependencyReplacingSampleModifierSpec extends Specification {

    @TempDir
    File tempDir

    def setup() {
        // Mock the latest versions for testing
        DependencyReplacingSampleModifier.latestNightlyVersion = "8.0-nightly"
        DependencyReplacingSampleModifier.latestReleasedVersion = GradleVersion.version("7.5")
    }

    def "should replace tooling API dependency if version is newer than latest released version"() {
        given:
        // Create a sample project directory with build scripts
        File buildScript = new File(tempDir, "build.gradle")
        buildScript << """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:7.6'
            }
            repositories {
                maven { url = 'libs-releases' }
            }
        """

        Sample sample = Mock(Sample) {
            getProjectDir() >> tempDir
        }

        when:
        new DependencyReplacingSampleModifier().modify(sample)

        then:
        buildScript.text == """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:8.0-nightly'
            }
            repositories {
                maven { url = 'libs-snapshots' }
            }
        """
    }

    def "should not replace tooling API dependency if version is equal to or older than the latest released version"() {
        given:
        File buildScript = new File(tempDir, "build.gradle")
        buildScript << """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:7.5'
            }
        """

        Sample sample = Mock(Sample) {
            getProjectDir() >> tempDir
        }

        when:
        new DependencyReplacingSampleModifier().modify(sample)

        then:
        buildScript.text == """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:7.5'
            }
        """
    }

    def "should handle build script without tooling API dependency"() {
        given:
        File buildScript = new File(tempDir, "build.gradle")
        buildScript << """
            dependencies {
                implementation 'org.gradle:some-other-library:1.2'
            }
        """

        Sample sample = Mock(Sample) {
            getProjectDir() >> tempDir
        }

        when:
        new DependencyReplacingSampleModifier().modify(sample)

        then:
        buildScript.text == """
            dependencies {
                implementation 'org.gradle:some-other-library:1.2'
            }
        """
    }

    def "should return replacement string if version is newer"() {
        given:
        String content = """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:7.6'
            }
        """

        when:
        Optional<String> result = DependencyReplacingSampleModifier.getReplacementString(content)

        then:
        result.isPresent()
        result.get() == """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:8.0-nightly'
            }
        """
    }

    def "should return empty Optional if version is not newer"() {
        given:
        String content = """
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:7.5'
            }
        """

        when:
        Optional<String> result = DependencyReplacingSampleModifier.getReplacementString(content)

        then:
        !result.isPresent()
    }
}
