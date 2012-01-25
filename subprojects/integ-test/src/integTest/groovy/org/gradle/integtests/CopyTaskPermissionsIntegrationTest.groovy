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
package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TemporaryFolder
import org.junit.Rule

class CopyTaskPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Rule def tmpDir = new TemporaryFolder()
    String tmpPath = tmpDir.dir.absolutePath

    def "permissions are preserved, overridden by type, and overridden by copy action"() {
        setup:
        def referenceArchive = createReferenceArchiveWithPermissions(0666)

        def buildScript = file("build.gradle") << """
            $assertModesFunction

            task copy(type: Tar) {
                from tarTree('${referenceArchive.absolutePath}')
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
        setup:
        def referenceArchive = createReferenceArchiveWithPermissions(0666)

        def buildScript = file("build.gradle") << """
            import static java.lang.Integer.toOctalString
            $assertModesFunction

            def preservedModes = [:]
            task copyPreserved(type: Tar) {
                from tarTree('${referenceArchive.absolutePath}')
                eachFile {
                    preservedModes[it.name] = toOctalString(it.mode)
                }
            }

            def overriddenModes = [:]
            task copyOverridden(type: Tar) {
                from tarTree('${referenceArchive.absolutePath}')
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
        def archive = new File(tmpDir.dir, "reference.tar")
        def archiveTmp = new File(tmpDir.dir, 'reference');

        createTestFile(archiveTmp, "file", false)
        createTestFile(archiveTmp, "script", false)
        createTestFile(archiveTmp, "folder", true)

        // create the archive, with correct permissions using a build script
        def script = file("create-reference-archive.gradle") << """
            import static java.lang.Integer.toOctalString
            $assertModesFunction

            task createReference(type: Tar) { 
                destinationDir = file('$tmpPath')
                archiveName = '${archive.name}'
                from file('${archiveTmp.absolutePath}')
                fileMode = $mode
                dirMode = $mode
            }

            task verifyReference(dependsOn: createReference) << {
                assertModes(tarTree(file('${archive.absolutePath}')), [
                    file: toOctalString($mode),
                    script: toOctalString($mode),
                    folder: toOctalString($mode)
                ])
            }
        """

        executer.usingBuildScript(script)
            .withTasks('verifyReference')
            .run();

        archive
    }

    private void createTestFile(File parent, String name, boolean directory) {
        def f = new File(parent, name)
        parent.mkdir()
        assert directory ? f.mkdir() : f.createNewFile()
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
