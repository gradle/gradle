/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import spock.lang.Issue

import static org.junit.Assume.assumeFalse

class ErrorPronePluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue("https://github.com/gradle/gradle/issues/9897")
    def 'errorprone plugin'() {
        // mavenCentralRepository() falls back to repo.maven.apache.org when no mirror is configured.
        // Abort before that call when this agent black-holes Maven Central, matching
        // MavenCentralDependencyResolveIntegrationTest on master and release.
        assumeFalse(
            "repo.maven.apache.org resolves to a loopback address on this agent, so Maven Central is deliberately unreachable",
            RepoScriptBlockUtil.mavenCentralMirrorUrl == ArtifactRepositoryContainer.MAVEN_CENTRAL_URL && mavenCentralBlackholed()
        )

        given:
        buildFile << """
            plugins {
                id('java')
                id("net.ltgt.errorprone") version "${TestedVersions.errorProne}"
            }

            ${mavenCentralRepository()}

            if (JavaVersion.current().java8) {
                dependencies {
                    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
                }
            }

            dependencies {
                errorprone("com.google.errorprone:error_prone_core:2.8.0")
            }

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                options.errorprone {
                    check("DoubleBraceInitialization", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
                }
            }
        """
        file("src/main/java/Test.java") << """
            import java.util.HashSet;
            import java.util.Set;

            public class Test {

                public static void main(String[] args) {
                }

            }
        """

        expect:
        runner('compileJava').build()
    }

    private static boolean mavenCentralBlackholed() {
        try {
            return InetAddress.getAllByName("repo.maven.apache.org").every { it.loopbackAddress }
        } catch (UnknownHostException ignored) {
            return false
        }
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'net.ltgt.errorprone': Versions.of(TestedVersions.errorProne)
        ]
    }
}
