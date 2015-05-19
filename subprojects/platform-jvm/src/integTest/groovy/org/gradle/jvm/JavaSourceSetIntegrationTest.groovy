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
package org.gradle.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class JavaSourceSetIntegrationTest extends AbstractIntegrationSpec {

    void setup() {
        EnableModelDsl.enable(super.executer)
    }

    def "can define dependencies on Java source set"() {
        given:
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
                        library 'someLib' // Library in same project
                        project 'otherProject' library 'someLib' // Library in other project
                        project 'otherProject' // Library in other project, expect exactly one library
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                def deps = $('components.main.sources.java').dependencies
                assert deps.size() == 3
                deps[0].libraryName == 'someLib'
                deps[1].projectPath == 'otherProject'
                deps[1].libraryName == 'fooLib'
                deps[2].projectPath == 'otherProject'
                deps[2].libraryName == null
            }
        }
    }
}
'''
        when:
        succeeds "checkDependencies"

        then:
        noExceptionThrown()
    }

}
