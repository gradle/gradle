/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.inputs.undeclared

import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest

class FileCollectionContentsChangingTest extends AbstractConfigurationCacheIntegrationTest {
    def "Details when file collection used during configuration changes"() {
        def configurationCache = newConfigurationCacheFixture()

        file("foo/a.txt") << """
            a!
        """
        buildFile("""
            abstract class MyTask extends DefaultTask {
                @Internal
                transient FileTree tree = project.fileTree("foo")

                @Internal
                Provider<String> filesString = project.provider {
                    tree.files.join("\\n")
                }

                @TaskAction def action() {
                    filesString.get()
                }
            }

            tasks.register("myTask", MyTask)
        """)

        when:
        configurationCacheRun("myTask")

        then:
        configurationCache.assertStateStored()

        when:
        file("foo/b.txt") << """
            b!
        """
        configurationCacheRun("myTask", "--info")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because a fingerprint of a file collection input with 2 files to unknown location has changed. First few files:")
        outputContains("/foo/a.txt")
        outputContains("/foo/b.txt")
    }
}
