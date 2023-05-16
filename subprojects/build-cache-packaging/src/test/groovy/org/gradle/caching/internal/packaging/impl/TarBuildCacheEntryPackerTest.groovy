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

package org.gradle.caching.internal.packaging.impl

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.Deleter
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.internal.file.TreeType.DIRECTORY
import static org.gradle.internal.file.TreeType.FILE

class TarBuildCacheEntryPackerTest extends AbstractTarBuildCacheEntryPackerSpec {
    @Override
    protected FilePermissionAccess createFilePermissionAccess() {
        new FilePermissionAccess() {
            @Delegate
            FileSystem fs = TestFiles.fileSystem()
        }
    }

    @Override
    protected Deleter createDeleter() {
        TestFiles.deleter()
    }

    def "can pack directory"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceSubDir = sourceOutputDir.file("subdir").createDir()
        def sourceDataFile = sourceSubDir.file("data.txt")
        sourceDataFile << "output"
        def targetOutputDir = temporaryFolder.file("target").createDir()
        def targetSubDir = targetOutputDir.file("subdir")
        def targetDataFile = targetSubDir.file("data.txt")
        def output = new ByteArrayOutputStream()
        when:
        def packResult = pack output, prop(DIRECTORY, sourceOutputDir)

        then:
        packResult.entries == 4

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        def result = unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        targetDataFile.text == "output"
        result.entries == 4
    }

    def "can pack tree with missing #type (pre-existing as: #preExistsAs)"() {
        def sourceOutput = temporaryFolder.file("source")
        def targetOutput = temporaryFolder.file("target")
        switch (preExistsAs) {
            case "file":
                targetOutput.createNewFile()
                break
            case "dir":
                targetOutput.createDir()
                break
            case "none":
                break
        }
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(type, sourceOutput)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(type, targetOutput)

        then:
        !targetOutput.exists()
        0 * _

        where:
        type      | preExistsAs
        FILE      | "file"
        FILE      | "dir"
        FILE      | "none"
        DIRECTORY | "file"
        DIRECTORY | "dir"
        DIRECTORY | "none"
    }

    def "can pack single tree file with #type name"() {
        def sourceOutputFile = temporaryFolder.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = temporaryFolder.file("target.txt")
        def output = new ByteArrayOutputStream()
        pack output, prop(FILE, sourceOutputFile)

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(FILE, targetOutputFile)

        then:
        targetOutputFile.text == "output"

        where:
        type      | treeName
        "long"    | "tree-" + ("x" * 100)
        "unicode" | "tree-dezső"
    }

    @Requires(UnitTestPreconditions.UnixDerivative)
    def "can pack tree directory with files having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        sourceOutputDir.file(fileName) << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file(fileName)
        def output = new ByteArrayOutputStream()
        pack output, prop(DIRECTORY, sourceOutputDir)

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        targetOutputFile.text == "output"

        where:
        type          | fileName
        "ascii-only"  | "input-file.txt"
        "chinese"     | "输入文件.txt"
        "hungarian"   | "Dezső.txt"
        "space"       | "input file.txt"
        "zwnj"        | "input\u200cfile.txt"
        "url-quoted"  | "input%<file>#2.txt"
    }

    def "can pack trees having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        sourceOutputDir.file("output.txt") << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file("output.txt")
        def output = new ByteArrayOutputStream()
        pack output, prop(treeName, DIRECTORY, sourceOutputDir)

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(treeName, DIRECTORY, targetOutputDir)

        then:
        targetOutputFile.text == "output"

        where:
        type          | treeName
        "ascii-only"  | "input-file"
        "chinese"     | "输入文件"
        "hungarian"   | "Dezső"
        "space"       | "input file"
        "zwnj"        | "input\u200cfile"
        "url-quoted"  | "input%<file>#2"
        "file-system" | ":input\\/file:"
    }

    def "can pack entity with all optional, null trees"() {
        when:
        def output = new ByteArrayOutputStream()
        pack output,
            prop("out1", FILE, null),
            prop("out2", DIRECTORY, null)

        then:
        noExceptionThrown()

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            prop("out1", FILE, null),
            prop("out2", DIRECTORY, null)

        then:
        noExceptionThrown()
    }

    def "can pack entity with missing files"() {
        def sourceDir = temporaryFolder.file("source")
        def missingSourceFile = sourceDir.file("missing.txt")
        def missingSourceDir = sourceDir.file("missing")
        def targetDir = temporaryFolder.file("target")
        def missingTargetFile = targetDir.file("missing.txt")
        def missingTargetDir = targetDir.file("missing")
        def output = new ByteArrayOutputStream()

        when:
        pack output,
            prop("missingFile", FILE, missingSourceFile),
            prop("missingDir", DIRECTORY, missingSourceDir)

        then:
        noExceptionThrown()

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            prop("missingFile", FILE, missingTargetFile),
            prop("missingDir", DIRECTORY, missingTargetDir)

        then:
        noExceptionThrown()
    }

    def "can pack entity with empty directory"() {
        def sourceDir = temporaryFolder.file("source").createDir()
        def targetDir = temporaryFolder.file("target")
        when:
        def output = new ByteArrayOutputStream()
        pack output, prop("empty", DIRECTORY, sourceDir)

        then:
        noExceptionThrown()

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop("empty", DIRECTORY, targetDir)

        then:
        targetDir.assertIsEmptyDir()
    }
}
