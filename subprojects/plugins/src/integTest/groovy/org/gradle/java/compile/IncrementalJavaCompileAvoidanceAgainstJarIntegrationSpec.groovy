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

package org.gradle.java.compile

import spock.lang.Issue
import spock.lang.Unroll

class IncrementalJavaCompileAvoidanceAgainstJarIntegrationSpec extends AbstractJavaCompileAvoidanceAgainstJarIntegrationSpec {
    def setup() {
        useJar()
        useIncrementalCompile()
    }

    @Unroll
    @Issue("gradle/gradle#1913")
    def "detects changes in compile classpath with #config change"() {
        given:
        buildFile << """
            apply plugin: 'java-library'
               
            repositories {
               jcenter()
            }
            
            dependencies {
               if (project.hasProperty('useCommons')) {
                  $config 'org.apache.commons:commons-lang3:3.5'
               }
               
               // There MUST be at least 3 dependencies, in that specific order, for the bug to show up.
               // The reason is that `IncrementalTaskInputs` reports wrong information about deletions at the
               // beginning of a list, when the collection is ordered. It has been agreed not to fix it now, but
               // rather change the incremental compiler not to rely on this incorrect information
               
               implementation 'net.jcip:jcip-annotations:1.0'
               implementation 'org.slf4j:slf4j-api:1.7.10'
            }
        """
        file("src/main/java/Client.java") << """import org.apache.commons.lang3.exception.ExceptionUtils;
            public class Client {
                public void doSomething() {
                    ExceptionUtils.rethrow(new RuntimeException("ok"));
                }
            }
        """

        when:
        executer.withArgument('-PuseCommons')
        succeeds ':compileJava'

        then:
        noExceptionThrown()

        when: "Apache Commons is removed from classpath"
        fails ':compileJava'

        then:
        failure.assertHasCause('Compilation failed; see the compiler error output for details.')

        where:
        config << ['api', 'implementation', 'compile']
    }
}
