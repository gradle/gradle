/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class FileCollectionConventionMappingIntegrationTest extends AbstractIntegrationSpec {
    //TODO-RC re-enable for 8.8
    @Ignore("https://github.com/gradle/gradle/pull/28135")
    def "convention mapping can be used with Configurable File Collection and an actual value"() {
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal abstract ConfigurableFileCollection getFoo()
                @Internal abstract ConfigurableFileCollection getBar()
                @Inject abstract ProjectLayout getLayout()

                @TaskAction
                void useIt() {
                    assert foo.files == layout.files("file1", "file2").files
                    assert foo.files.size() == 2
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo") { bar }
                bar.setFrom("file1", "file2")
            }
        """

        expect:
        succeeds 'mytask'
    }

    def "convention mapping works with FileCollection in a ConventionTask"() {
        buildFile << """
            abstract class MyTask extends org.gradle.api.internal.ConventionTask {
                @Internal abstract ConfigurableFileCollection getFoo()
                @Inject abstract ProjectLayout getLayout()

                @TaskAction
                void useIt() {
                    // convention mapping for FileCollection is already ignored
                    assert foo.files == layout.files("file1", "file2").files
                    assert foo.files.size() == 2
                }
            }
            tasks.register("mytask", MyTask) {
                conventionMapping.map("foo", { project.objects.fileCollection().from("file1") })
                foo.convention("file1", "file2")
            }
        """

        expect:
        succeeds 'mytask'
    }
}
