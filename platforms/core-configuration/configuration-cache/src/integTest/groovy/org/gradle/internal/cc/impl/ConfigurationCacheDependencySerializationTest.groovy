/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

/**
 * Tests for serializing Dependency Management types using the Configuration Cache.
 */
class ConfigurationCacheDependencySerializationTest extends AbstractConfigurationCacheIntegrationTest {
    def "configuration cache can serialize FileCollectionDependency"() {
        when:
        buildFile <<  """
            def fileName = 'foo.txt'
            def someDependency = project.dependencies.create(files(fileName))
            assert someDependency instanceof org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency

            task checkDeps {
                def dep = someDependency
                doLast {
                    assert someDependency.files.singleFile.name == fileName
                }
            }
        """

        then:
        succeeds 'checkDeps'
    }
}
