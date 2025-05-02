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
        failure.assertHasCause('Compilation failed; see the compiler output below.')
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
        failure.assertHasCause """The consumer was configured to find a component for use during runtime, compatible with Java 7, and its dependencies declared externally. There are several available matching variants of org.junit.jupiter:junit-jupiter-api:5.6.0
The only attribute distinguishing these variants is 'org.gradle.docstype'. Add this attribute to the consumer's configuration to resolve the ambiguity:
  - Value: 'javadoc' selects variant: 'javadocElements'
  - Value: 'sources' selects variant: 'sourcesElements'"""
    }
}
