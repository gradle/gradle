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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class CompositeBuildConfigurationAttributesResolveIntegrationTest  extends AbstractIntegrationSpec {
    def "context travels down to transitive dependencies with composite builds"() {
        given:
        file('settings.gradle') << """
            include 'a', 'b'
            includeBuild 'includedBuild'
        """
        buildFile << '''
            project(':a') {
                configurations {
                    _compileFreeDebug.attributes(buildType: 'debug', flavor: 'free')
                    _compileFreeRelease.attributes(buildType: 'release', flavor: 'free')
                }
                dependencies {
                    _compileFreeDebug project(':b')
                    _compileFreeRelease project(':b')
                }
                task checkDebug(dependsOn: configurations._compileFreeDebug) {
                    doLast {
                       assert configurations._compileFreeDebug.collect { it.name } == ['b-transitive.jar', 'c-foo.jar']
                    }
                }
                task checkRelease(dependsOn: configurations._compileFreeRelease) {
                    doLast {
                       assert configurations._compileFreeRelease.collect { it.name } == ['b-transitive.jar', 'c-bar.jar']
                    }
                }

            }
            project(':b') {
                configurations.create('default') {

                }
                artifacts {
                    'default' file('b-transitive.jar')
                }
                dependencies {
                    'default'('com.acme.external:external:1.0')
                }
            }
        '''

        file('includedBuild/build.gradle') << '''

            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'
            configurations {
                foo.attributes(buildType: 'debug', flavor: 'free')
                bar.attributes(buildType: 'release', flavor: 'free')
            }
            task fooJar(type: Jar) {
               baseName = 'c-foo'
            }
            task barJar(type: Jar) {
               baseName = 'c-bar'
            }
            artifacts {
                foo fooJar
                bar barJar
            }
        '''
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''

        when:
        run ':a:checkDebug'

        then:
        executedAndNotSkipped ':external:fooJar'
        notExecuted ':external:barJar'

        when:
        run ':a:checkRelease'

        then:
        executedAndNotSkipped ':external:barJar'
        notExecuted ':external:fooJar'
    }

}
