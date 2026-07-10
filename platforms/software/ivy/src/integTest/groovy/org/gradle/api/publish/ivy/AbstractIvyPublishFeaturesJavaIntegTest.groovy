/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyJavaModule

abstract class AbstractIvyPublishFeaturesJavaIntegTest extends AbstractIvyPublishIntegTest {
    IvyFileModule module = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
    IvyJavaModule javaLibrary = javaLibrary(module)

    def setup() {
        createBuildScripts("""
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
            }
            group = 'org.gradle.test'
            version = '1.9'

$append
"""

    }

    @Override
    protected ExecutionResult run(String... tasks) {
        try {
            return super.run(tasks)
        } finally {
            module.removeGradleMetadataRedirection()
        }
    }
}
