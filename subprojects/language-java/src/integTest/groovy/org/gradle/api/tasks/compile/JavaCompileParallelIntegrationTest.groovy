/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({GradleContextualExecuter.parallel})
class JavaCompileParallelIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Issue("https://issues.gradle.org/browse/GRADLE-3029")
    def "system property java.home is not modified across compile task boundaries"() {
        def module = mavenHttpRepo.module('foo', 'bar')
        module.publish()
        def projectNames = ['a', 'b', 'c', 'd', 'e', 'f', 'g']

        file('settings.gradle') << "include ${projectNames.collect { "'$it'" }.join(', ')}"
        file('build.gradle') << """
            subprojects {
                apply plugin: 'java'

                repositories {
                    maven { url '${mavenHttpRepo.uri}' }
                }

                dependencies {
                    compile 'foo:bar:1.0'
                }
            }
"""

        projectNames.each { projectName ->
            file("${projectName}/src/main/java/Foo.java") << 'public class Foo { }'
        }

        when:
        module.allowAll()
        args('--parallel-threads=4')
        run('compileJava')

        then:
        noExceptionThrown()

        projectNames.each { projectName ->
            file("${projectName}/build/classes/main/Foo.class").exists()
        }
    }
}
