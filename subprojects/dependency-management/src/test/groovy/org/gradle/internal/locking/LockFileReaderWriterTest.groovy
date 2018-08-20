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

import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.file.FileResolver
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class LockFileReaderWriterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    TestFile lockDir = tmpDir.createDir(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER)

    @Subject
    LockFileReaderWriter lockFileReaderWriter
    FileResolver resolver = Mock()
    DomainObjectContext context = Mock()

    def setup() {
        context.identityPath(_) >> { String value -> Path.path(value) }
        resolver.canResolveRelativePath() >> true
        resolver.resolve(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER) >> lockDir
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, true)
    }

    def 'writes a lock file on persist'() {
        when:
        lockFileReaderWriter.writeLockFile('conf', ['line1', 'line2'])

        then:
        lockDir.file('conf.lockfile').text == """${LockFileReaderWriter.LOCKFILE_HEADER}line1
line2
"""
    }

    def 'reads a lock file'() {
        given:
        lockDir.file('conf.lockfile') << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']
    }

    def 'writes a lock file with prefix on persist'() {
        when:
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, false)
        lockFileReaderWriter.writeLockFile('conf', ['line1', 'line2'])

        then:
        lockDir.file('buildscript-conf.lockfile').text == """${LockFileReaderWriter.LOCKFILE_HEADER}line1
line2
"""
    }

    def 'reads a lock file with prefix'() {
        given:
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, false)
        lockDir.file('buildscript-conf.lockfile') << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']
    }

    def 'fails to read a lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, true)

        when:
        lockFileReaderWriter.readLockFile('foo')

        then:
        def ex = thrown(IllegalStateException)
        1 * context.identityPath('foo') >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for configuration')
        ex.getMessage().contains('foo')
    }

    def 'fails to write a lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, true)

        when:
        lockFileReaderWriter.writeLockFile('foo', [])

        then:
        def ex = thrown(IllegalStateException)
        1 * context.identityPath('foo') >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for configuration')
        ex.getMessage().contains('foo')
    }
}
