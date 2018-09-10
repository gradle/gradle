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

class JavaLibraryConsumptionIntegrationTest extends AbstractIntegrationSpec {

    def "runtime dependencies from maven modules do not leak into compile classpath"() {
        given:
        buildFile << """
            apply plugin: 'java-library'
            ${jcenterRepository()}
            dependencies {
                implementation 'io.reactivex:rxnetty:0.4.4'
            }
            task checkForRxJavaDependency {
                assert configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.find { it.requested.displayName == 'io.reactivex:rxjava:1.0.1' }
                assert !configurations.compileClasspath.incoming.resolutionResult.allDependencies.find { it.requested.displayName == 'io.reactivex:rxjava:1.0.1' }
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
}
