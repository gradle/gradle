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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class CopyTaskFilePermissionsIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    def "mode sets file permissions"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    mode = 0777
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("rwxrwxrwx")
    }

    def "permissions block overrides mode"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    mode = 0777
                    permissions {}
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("rw-r--r--")
    }

    def "permissions block sets sensible defaults"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    permissions {}
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("rw-r--r--")
    }

    def "permissions block can customize permissions (Groovy DSL)"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    permissions {
                        all {
                            read = true
                            write = true
                            execute = true
                        }
                        user {
                            write = false
                        }
                        user.execute = false
                        group.write = false
                        other {
                            execute = false
                        }
                    }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r--r-xrw-")
    }

    def "permissions block can customize permissions (Kotlin DSL)"() {
        given:
        withSourceFiles("r--------")

        buildFile.delete()
        buildKotlinFile.text = '''
            tasks.register<Copy>("copy") {
               from("files")
               into("dest")
               eachFile {
                    permissions {
                        all {
                            read.set(true)
                            write.set(true)
                            execute.set(true)
                        }
                        user {
                            write.set(false)
                        }
                        user.execute.set(false)
                        group.write.set(false)
                        other {
                            execute.set(false)
                        }
                    }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r--r-xrw-")
    }

    def "permissions can be created via factory"() {
        given:
        withSourceFiles("r--------")
        buildScript '''
            def p = project.services.get(FileSystemOperations).permissions(true) {
                all {
                    read = true
                    write = true
                    execute = true
                }
                user {
                    write = false
                }
                user.execute = false
                group.write = false
                other {
                    execute = false
                }
            }

            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               eachFile {
                    permissions.set(p)
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        assertDestinationFilePermissions("r--r-xrw-")
    }

    private void withSourceFiles(String permissions) {
        file("files/sub/a.txt").createFile().setPermissions(permissions)
        file("files/sub/dir/b.txt").createFile().setPermissions(permissions)
        file("files/c.txt").createFile().setPermissions(permissions)
        file("files/sub/empty").createDir().setPermissions(permissions)
    }

    def assertDestinationFilePermissions(String permissions) {
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'c.txt',
            'sub/empty'
        )
        assert file("dest/sub/a.txt").permissions == permissions
        file("dest/sub/dir/b.txt").permissions == permissions
        file("dest/c.txt").permissions == permissions
        file("dest/sub/empty").permissions == "r--------" // eachFile doesn't cover directories
    }
}
