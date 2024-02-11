/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.file.collections

import com.google.common.base.Throwables
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.AccessDeniedException

@Requires(UnitTestPreconditions.FilePermissions)
@Issue('https://github.com/gradle/gradle/issues/2639')
class DefaultUnauthorizedDirectoryWalkerTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def rootDir

    def setup() {
        rootDir = tmpDir.createDir('root')
        rootDir.createFile('unauthorized/file')
        rootDir.createDir('authorized')
        rootDir.file('unauthorized').mode = 0000
    }

    def cleanup() {
        rootDir.file('unauthorized').mode = 0777
    }

    def "excluded files' permissions should be ignored"() {
        when:
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet().exclude('unauthorized'), TestFiles.fileSystem(), false)
        def visitedDirectories = []
        def fileVisitor = [visitDir: { visitedDirectories << it }] as FileVisitor

        fileTree.visit(fileVisitor)

        then:
        visitedDirectories.size() == 1
        visitedDirectories.first().name == 'authorized'
    }

    def "throw exception when unauthorized"() {
        when:
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), TestFiles.fileSystem(), false)
        def fileVisitor = Mock(FileVisitor)

        fileTree.visit(fileVisitor)

        then:
        Exception exception = thrown()
        Throwables.getRootCause(exception).class == AccessDeniedException
    }
}
