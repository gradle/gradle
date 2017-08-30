package org.gradle.api.internal.file.collections.jdk7

import com.google.api.client.repackaged.com.google.common.base.Throwables
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Issue

import java.nio.file.AccessDeniedException

@Requires(TestPrecondition.FILE_PERMISSIONS)
@Issue('https://github.com/gradle/gradle/issues/2639')
class Jdk7UnauthorizedDirectoryWalkerTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def rootDir
    def walkerInstance = new Jdk7DirectoryWalker()

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
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet().exclude('unauthorized'), { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        def visitedDirectories = []
        def fileVisitor = [visitDir: { visitedDirectories << it }] as FileVisitor

        fileTree.visit(fileVisitor)

        then:
        visitedDirectories.size() == 1
        visitedDirectories.first().name == 'authorized'
    }

    def "throw exception when unauthorized"() {
        when:
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        def fileVisitor = Mock(FileVisitor)

        fileTree.visit(fileVisitor)

        then:
        Exception exception = thrown()
        Throwables.getRootCause(exception).class == AccessDeniedException
    }
}
