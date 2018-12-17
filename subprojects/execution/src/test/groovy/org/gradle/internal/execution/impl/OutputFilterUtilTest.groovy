/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.execution.impl.OutputFilterUtil.filterOutputFingerprint

class OutputFilterUtilTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), fileSystemMirror)
    def outputFingerprinter = new OutputFileCollectionFingerprinter(new StringInterner(), snapshotter)

    def "pre-existing directories are filtered"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def beforeExecution = fingerprintOutput(outputDir)
        outputDir.file()

        when:
        def filteredOutputs = filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, beforeExecution)
        then:
        filteredOutputs.empty

        when:
        def outputDirFile = outputDir.file("in-output-dir").createFile()
        fileSystemMirror.beforeBuildFinished()
        def afterExecution = fingerprintOutput(outputDir)
        filteredOutputs = filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution)
        then:
        filteredOutputs.fingerprints.keySet() == [outputDir.absolutePath, outputDirFile.absolutePath] as Set
    }

    def "only newly created files in directory are part of filtered outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        outputDir.file("outputOfOther").createFile()
        def beforeExecution = fingerprintOutput(outputDir)

        when:
        def filteredOutputs = filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, beforeExecution)
        then:
        filteredOutputs.empty

        when:
        def outputOfCurrent = outputDir.file("outputOfCurrent").createFile()
        def afterExecution = fingerprintOutput(outputDir)
        filteredOutputs = filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution)
        then:
        filteredOutputs.fingerprints.keySet() == [outputDir.absolutePath, outputOfCurrent.absolutePath] as Set
    }

    def "previous outputs remain outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def outputDirFile = outputDir.file("outputOfCurrent").createFile()
        def previousExecution = fingerprintOutput(outputDir)
        outputDir.file("outputOfOther").createFile()
        def beforeExecution = fingerprintOutput(outputDir)

        when:
        def filteredOutputs = filterOutputFingerprint(previousExecution, beforeExecution, beforeExecution)
        then:
        filteredOutputs.fingerprints.keySet() == [outputDir, outputDirFile]*.absolutePath as Set
    }
    
    def "missing files are ignored"() {
        def missingFile = temporaryFolder.file("missing")
        def beforeExecution = fingerprintOutput(missingFile)
        expect:
        filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, beforeExecution).empty
    }

    def "added empty dir is captured"() {
        def emptyDir = temporaryFolder.file("emptyDir").createDir()
        def afterExecution = fingerprintOutput(emptyDir)
        expect:
        filterOutputFingerprint(outputFingerprinter.empty(), outputFingerprinter.empty(), afterExecution).fingerprints.keySet() == [emptyDir.absolutePath] as Set
        filterOutputFingerprint(outputFingerprinter.empty(), afterExecution, afterExecution).empty
    }

    private CurrentFileCollectionFingerprint fingerprintOutput(File output) {
        fileSystemMirror.beforeBuildFinished()
        outputFingerprinter.fingerprint(ImmutableFileCollection.of(output))
    }
    
    def "updated files in output directory are part of the output"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def existingFile = outputDir.file("some").createFile()
        def beforeExecution = fingerprintOutput(outputDir)
        existingFile << "modified"
        def afterExecution = fingerprintOutput(outputDir)
        expect:
        filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution).fingerprints.keySet() == [outputDir, existingFile]*.absolutePath as Set
    }

    def "updated files are part of the output"() {
        def existingFile = temporaryFolder.file("some").createFile()
        def beforeExecution = fingerprintOutput(existingFile)
        existingFile << "modified"
        def afterExecution = fingerprintOutput(existingFile)
        expect:
        filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution).fingerprints.keySet() == [existingFile.absolutePath] as Set
    }

    def "removed files are not considered outputs"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def outputDirFile = outputDir.file("toBeDeleted").createFile()
        def beforeExecution = fingerprintOutput(outputDir)
        outputDirFile.delete()
        def afterExecution = fingerprintOutput(outputDir)

        expect:
        filterOutputFingerprint(beforeExecution, beforeExecution, afterExecution).fingerprints.keySet() == [outputDir.absolutePath] as Set
        filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution).empty
    }

    def "overlapping directories are not included"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        outputDir.createDir("output-dir-2")
        def beforeExecution = fingerprintOutput(outputDir)
        def outputDirFile = outputDir.createFile("outputDirFile")
        def afterExecution1 = fingerprintOutput(outputDir)

        expect:
        filterOutputFingerprint(outputFingerprinter.empty(), beforeExecution, afterExecution1).fingerprints.keySet() == [outputDir, outputDirFile]*.absolutePath as Set
    }
}
