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

package org.gradle.internal.locking

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import org.gradle.util.GradleVersion

class LockFileReaderWriterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    TestFile lockDir = tmpDir.createDir(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER)

    @Subject
    LockFileReaderWriter lockFileReaderWriter
    FileResolver resolver = Mock()
    ProjectIdentity identity = new ProjectIdentity(new DefaultBuildIdentifier(Path.ROOT), Path.path("foo"), Path.path("foo"), "foo")
    DomainObjectContext context = Mock() {
        identityPath(_) >> { String value -> Path.path(value) }
        getProjectIdentity() >> identity
        getDisplayName() >> identity.displayName
    }
    FileResourceListener listener = Mock()
    RegularFileProperty lockFile = TestFiles.filePropertyFactory().newFileProperty()

    def setup() {
        resolver.canResolveRelativePath() >> true
        resolver.resolve(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER) >> lockDir
        resolver.resolve(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME) >> tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)
    }

    def 'writes a unique lock file'() {
        when:
        lockDir.deleteDir()
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME).text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()
        !lockDir.exists()
    }

    def 'deletes an unique lock file on empty lock state'() {
        def uniqueLockfile = tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)

        given:
        uniqueLockfile << 'Some file content'

        when:
        lockFileReaderWriter.writeUniqueLockfile(Collections.emptyMap());

        then:
        !uniqueLockfile.exists()
    }

    def 'writes dependencies and configurations sorted in the unique lock file'() {
        when:
        lockFileReaderWriter.writeUniqueLockfile([b: ['foo', 'bar'], d: ['bar', 'foobar'],a: ['foo'], e: [], f: [], c: []])

        then:
        tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME).text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=b,d
foo=a,b
foobar=d
empty=c,e,f
""".denormalize()
    }

    def 'writes a unique lock file to a custom location'() {
        def testLockFile = tmpDir.file('different', 'lock.file')
        lockDir.deleteDir()
        given:
        lockFile.set(testLockFile)

        when:
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        testLockFile.text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()
        !lockDir.exists()
    }

    def 'reads a legacy lock file'() {
        given:
        def lockFile = lockDir.file('conf.lockfile')
        lockFile << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']

        1 * listener.fileObserved(lockFile)
    }

    def 'reads a unique lock file'() {
        given:
        def lockFile = tmpDir.file('gradle.lockfile')
        lockFile << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]

        1 * listener.fileObserved(lockFile)
    }

    def 'reads an invalid unique lock file '() {
        given:
        def lockFile = tmpDir.file('gradle.lockfile')
        lockFile << """#ignored
<<<<<<< HEAD
======
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        def ex = thrown(InvalidLockFileException)
        ex.message == "Invalid lock state for lock file specified in '${lockFile.path}'. Line: '<<<<<<< HEAD'"
        ex.resolutions == ["Verify the lockfile content. For more information on lock file format, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/dependency_locking.html#lock_state_location_and_format in the Gradle documentation."]
    }

    def 'reads a unique lock file from a custom location'() {
        given:
        def file = tmpDir.file('custom', 'lock.file')
        lockFile.set(file)
        file << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]
    }

    def 'writes a unique lock file with prefix'() {
        when:
        context.isScript() >> true
        resolver.resolve("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME") >> tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME")
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME").text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()

    }

    def 'reads a legacy lock file with prefix'() {
        given:
        context.isScript() >> true
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)
        lockDir.file('buildscript-conf.lockfile') << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']
    }

    def 'reads a unique lock file with prefix'() {
        given:
        context.isScript() >> true
        resolver.resolve("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME") >> tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME")
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)
        tmpDir.file('buildscript-gradle.lockfile') << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]
    }

    def 'reads an invalid unique lock file with prefix'() {
        given:
        context.isScript() >> true
        resolver.resolve("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME") >> tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME")
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)
        def lockFile = tmpDir.file('buildscript-gradle.lockfile')
        lockFile << """#ignored
<<<<<<< HEAD
======
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        def ex = thrown(InvalidLockFileException)
        ex.message == "Invalid lock state for lock file specified in '${lockFile.path}'. Line: '<<<<<<< HEAD'"
        ex.resolutions == ["Verify the lockfile content. For more information on lock file format, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/dependency_locking.html#lock_state_location_and_format in the Gradle documentation."]
    }

    def 'fails to read a unique lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)

        when:
        lockFileReaderWriter.readUniqueLockFile()

        then:
        def ex = thrown(IllegalStateException)
        ex.getMessage().contains("Dependency locking cannot be used for project foo")
    }

    def 'fails to read a legacy lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)

        when:
        lockFileReaderWriter.readLockFile('foo')

        then:
        def ex = thrown(IllegalStateException)
        ex.getMessage().contains("Dependency locking cannot be used for project foo")
    }

    def 'fails to write a unique lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile, listener)

        when:
        lockFileReaderWriter.writeUniqueLockfile([foo: ['a:b:1.0']])

        then:
        def ex = thrown(IllegalStateException)
        ex.getMessage().contains("Dependency locking cannot be used for project foo")
    }
}
