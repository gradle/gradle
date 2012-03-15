/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import org.gradle.util.TextUtil

class ArchiveTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def "permissions are preserved, overridden by type, and overridden by copy action"() {
        def referenceArchive = createReferenceArchiveWithPermissions(0666)

        def buildScript = file("build.gradle") << """
            $assertModesFunction

            task copy(type: Tar) {
                from tarTree('${TextUtil.escapeString(referenceArchive.absolutePath)}')
                dirMode = 0777
                eachFile {
                    if (it.name == 'script') {
                        it.mode = 0123;
                    }
                }
            }

            task test(dependsOn: copy) << {
                assertModes(tarTree(copy.archivePath), [
                    file: '666',    // preserved
                    script: '123',  // overridden by dirMode
                    folder: '777'   // overridden by copy action
                ])
            }
        """

        when:
        executer.usingBuildScript(buildScript)
            .withTasks('test')
            .run()

        then:
        noExceptionThrown()
    }

    def "expected permissions are exposed to copy action"() {
        def referenceArchive = createReferenceArchiveWithPermissions(0666)

        def buildScript = file("build.gradle") << """
            import static java.lang.Integer.toOctalString
            $assertModesFunction

            def preservedModes = [:]
            task copyPreserved(type: Tar) {
                from tarTree('${TextUtil.escapeString(referenceArchive.absolutePath)}')
                eachFile {
                    preservedModes[it.name] = toOctalString(it.mode)
                }
            }

            def overriddenModes = [:]
            task copyOverridden(type: Tar) {
                from tarTree('${TextUtil.escapeString(referenceArchive.absolutePath)}')
                fileMode = 0123
                eachFile {
                    overriddenModes[it.name] = toOctalString(it.mode)
                }
            }

            task test(dependsOn: [copyPreserved, copyOverridden]) << {
                assert preservedModes == [file: "666", script: "666"]
                assert overriddenModes == [file: "123", script: "123"]
            }
        """

        when:
        executer.usingBuildScript(buildScript)
            .withTasks('test')
            .run()

        then:
        noExceptionThrown()
    }

    /*
     * Creates a TAR archive with three files, 'file', 'script' and 'folder'. These files
     * are used as reference data for the tests. The TAR archive is used to make the tests
     * independent of the file system, which may not support Unix permissions.
     */
    private File createReferenceArchiveWithPermissions(int mode) {
        def archive = tmpDir.file("reference.tar")
        def archiveTmp = tmpDir.createDir('reference')

        archiveTmp.createFile("file")
        archiveTmp.createFile("script")
        archiveTmp.createDir("folder")

        // create the archive, with correct permissions using a build script
        def script = file("create-reference-archive.gradle") << """
            import static java.lang.Integer.toOctalString
            $assertModesFunction

            task createReference(type: Tar) { 
                destinationDir = file('${TextUtil.escapeString(tmpDir.dir)}')
                archiveName = '${TextUtil.escapeString(archive.name)}'
                from file('${TextUtil.escapeString(archiveTmp.absolutePath)}')
                fileMode = $mode
                dirMode = $mode
            }

            task verifyReference(dependsOn: createReference) << {
                assertModes(tarTree(file('${TextUtil.escapeString(archive.absolutePath)}')), [
                    file: toOctalString($mode),
                    script: toOctalString($mode),
                    folder: toOctalString($mode)
                ])
            }
        """

        executer.usingBuildScript(script)
            .withTasks('verifyReference')
            .run()

        archive
    }

    // script fragment for extracting & checking files/modes from a FileTree
    def assertModesFunction = """
        def assertModes = { files, expectedModes ->
            def actualModes = [:]
            files.visit {
                actualModes[it.name] = Integer.toOctalString(it.mode)
            }
            assert expectedModes == actualModes
        }
    """
}
