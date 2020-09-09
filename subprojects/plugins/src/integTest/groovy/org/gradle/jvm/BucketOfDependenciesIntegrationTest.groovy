/*
 * Copyright 2020 the original author or authors.
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

class BucketOfDependenciesIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'mylib'
        """
        buildFile << """
            plugins {
                id 'jvm-ecosystem'
            }
            def jvm = extensions.create(org.gradle.api.plugins.jvm.internal.JvmPluginExtension, "jvm", org.gradle.api.plugins.jvm.internal.DefaultJvmPluginExtension)

            group = 'com.acme'
            version = '1.4'
        """
    }

    def "bucket of dependencies is registered lazily"() {
        buildFile << """
            def bucket = jvm.utilities.registerDependencyBucket('test', 'some description')
            bucket.configure {
                throw new RuntimeException("Should't be called eagerly")
            }
            def bucket2 = jvm.utilities.registerDependencyBucket('other', 'some other description')

            tasks.register("dump") {
                doLast {
                    assert configurations.other.description == 'some other description'
                }
            }
        """

        when:
        succeeds 'dump'

        then:
        executedAndNotSkipped ':dump'
    }
}
