/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class JavaLibraryConsumptionIntegrationTest extends AbstractIntegrationSpec {

    def "runtime dependencies from maven modules do not leak into compile classpath"() {
        given:
        buildFile << """
            apply plugin: 'java-library'
            ${mavenCentralRepository()}
            dependencies {
                implementation 'io.reactivex:rxnetty:0.4.4'
            }

            def displayNamesOf(config) {
                provider { config.incoming.resolutionResult.allDependencies*.requested.displayName }
            }
            task checkForRxJavaDependency {
                def runtimeClasspathNames = displayNamesOf(configurations.runtimeClasspath)
                def compileClasspathNames = displayNamesOf(configurations.compileClasspath)
                doLast {
                    assert runtimeClasspathNames.get().find { it == 'io.reactivex:rxjava:1.0.1' }
                    assert !compileClasspathNames.get().find { it == 'io.reactivex:rxjava:1.0.1' }
                }
            }
        """

        when:
        //compilation should fail, as `rx.observers.Observable` is part of RxJava 1.x, which is a runtime-only dependency.
        file('src/main/java/App.java') << 'public class App { public void run() { rx.observers.Observers.empty(); } }'

        then:
        fails 'checkForRxJavaDependency', 'build'
        failure.assertHasCause('Compilation failed; see the compiler error output for details.')
        failure.assertHasErrorOutput('error: package rx.observers does not exist')
    }

    @Issue("https://github.com/gradle/gradle/issues/11995")
    def "provides a human understable error message when some variants were discarded and the remainder is ambiguous"() {
        buildFile << """
            apply plugin: 'java-base'

            configurations {
                consumer {
                    assert canBeResolved
                    canBeConsumed = false
                    attributes {
                        // intentionally not complete
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 7)
                    }
                }
            }

            ${mavenCentralRepository()}
            dependencies {
                consumer "org.junit.jupiter:junit-jupiter-api:5.6.0"
            }

            tasks.register("resolve") {
                def files = provider { configurations.consumer.files }
                doLast {
                    println files.get()
                }
            }
        """

        when:
        fails 'resolve'

        then:
        failure.assertHasCause """The consumer was configured to find a component for use during runtime, compatible with Java 7, and its dependencies declared externally. However we cannot choose between the following variants of org.junit.jupiter:junit-jupiter-api:5.6.0:
  - javadocElements
  - sourcesElements
All of them match the consumer attributes:
  - Variant 'javadocElements' capability org.junit.jupiter:junit-jupiter-api:5.6.0 declares a component for use during runtime, and its dependencies declared externally:
      - Unmatched attributes:
          - Provides documentation but the consumer didn't ask for it
          - Provides javadocs but the consumer didn't ask for it
          - Doesn't say anything about its target Java version (required compatibility with Java 7)
          - Provides release status but the consumer didn't ask for it
  - Variant 'sourcesElements' capability org.junit.jupiter:junit-jupiter-api:5.6.0 declares a component for use during runtime, and its dependencies declared externally:
      - Unmatched attributes:
          - Provides documentation but the consumer didn't ask for it
          - Provides sources but the consumer didn't ask for it
          - Doesn't say anything about its target Java version (required compatibility with Java 7)
          - Provides release status but the consumer didn't ask for it
The following variants were also considered but didn't match the requested attributes:
  - Variant 'apiElements' capability org.junit.jupiter:junit-jupiter-api:5.6.0 declares a component, and its dependencies declared externally:
      - Incompatible because this component declares a component for use during compile-time, compatible with Java 8 and the consumer needed a component for use during runtime, compatible with Java 7
  - Variant 'runtimeElements' capability org.junit.jupiter:junit-jupiter-api:5.6.0 declares a component for use during runtime, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 8 and the consumer needed a component, compatible with Java 7"""
    }
}
