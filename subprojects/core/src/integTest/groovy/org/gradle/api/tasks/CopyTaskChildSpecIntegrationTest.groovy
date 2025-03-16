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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache.Skip.INVESTIGATE

class CopyTaskChildSpecIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE)
    def "changing child specs of the copy task while executing is disallowed"() {
        given:
        file("some-dir/input.txt") << "Data"
        buildFile """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                from ("some-dir")
                into ("build/output")

                doFirst {
                    from ("some-other-dir") {
                        exclude "non-existent-file"
                    }
                }
            }
        """

        when:
        fails "copy"

        then:
        failure.assertHasCause("You cannot add child specs at execution time. Consider configuring this task during configuration time or using a separate task to do the configuration.")
    }

    @Issue("https://github.com/gradle/gradle/issues/25924")
    def "can query file and dir mode if set in the parent"() {
        given:
        file("root/root-file.txt") << 'root'
        buildFile("""
            def baseSpec = copySpec {
                from("root") {
                    println(filePermissions.getOrNull() == null ? "DEFAULT" : filePermissions.get().toUnixNumeric())
                    dirPermissions {unix(0755) }
                    println(dirPermissions.getOrNull() == null ? "DEFAULT" : dirPermissions.get().toUnixNumeric())
                    dirPermissions {unix(0755) }
                }
            }

            tasks.register("copy", Copy) {
                println(filePermissions.getOrNull() == null ? "DEFAULT" : filePermissions.get().toUnixNumeric())
                dirPermissions {unix(0755) }
                println(dirPermissions.getOrNull() == null ? "DEFAULT" : dirPermissions.get().toUnixNumeric())
                dirPermissions {unix(0755) }
                into("build-output")
                with baseSpec
            }
        """)

        expect:
        succeeds "copy"
    }
}
