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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaLanguageDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve dependency on local library"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        dep(JvmLibrarySpec)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        succeeds 'assemble'

    }

    def "should fail if library doesn't exist"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'someLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        fails 'assemble'
        failure.assertHasCause("Could not resolve dependency 'project : library someLib'")
    }
}
