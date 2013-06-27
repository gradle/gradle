/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.api.internal.file

import org.apache.commons.io.FileUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.copy.CopyActionImpl
import org.gradle.api.internal.file.copy.CopySpecImpl
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecException
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ClasspathUtil
import org.junit.Rule
import org.junit.Test
import spock.lang.Specification

public class DefaultFileOperationsTest extends Specification {
    private final FileResolver resolver = Mock()
    private final TaskResolver taskResolver = Mock()
    private final TemporaryFileProvider temporaryFileProvider = Mock()
    private final Instantiator instantiator = Mock()
    private DefaultFileOperations fileOperations = new DefaultFileOperations(resolver, taskResolver, temporaryFileProvider, instantiator)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def resolvesFile() {
        when:
        TestFile file = expectPathResolved('path')

        then:
        fileOperations.file('path') == file
    }

    def resolvesFileWithValidation() {
        TestFile file = tmpDir.file('path')
        resolver.resolve('path', PathValidation.EXISTS) >> file

        expect:
        fileOperations.file('path', PathValidation.EXISTS) == file
    }

    def resolvesURI() {
        when:
        URI uri = expectPathResolvedToUri('path')

        then:
        fileOperations.uri('path') == uri
    }

    def resolvesFiles() {
        when:
        def fileCollection = fileOperations.files('a', 'b')

        then:
        fileCollection instanceof DefaultConfigurableFileCollection
        fileCollection.from == ['a', 'b'] as LinkedHashSet
        fileCollection.resolver.is(resolver)
        fileCollection.buildDependency.resolver.is(taskResolver)
    }

    def createsFileTree() {
        TestFile baseDir = expectPathResolved('base')

        when:
        def fileTree = fileOperations.fileTree('base')

        then:
        fileTree instanceof FileTree
        fileTree.dir == baseDir
        fileTree.resolver.is(resolver)
    }

    def createsAndConfiguresFileTree() {
        given:
        TestFile baseDir = expectPathResolved('base')
        
        when:
        def fileTree = fileOperations.fileTree('base') {
            builtBy 1
        }
        
        then:
        fileTree instanceof FileTree
        fileTree.dir == baseDir
        fileTree.resolver.is(resolver)
        fileTree.builtBy == [1] as Set
    }
    
    def createsFileTreeFromMap() {
        TestFile baseDir = expectPathResolved('base')

        when:
        def fileTree = fileOperations.fileTree(dir: 'base')

        then:
        fileTree instanceof FileTree
        fileTree.dir == baseDir
        fileTree.resolver.is(resolver)
    }

    @Test
    public void createsFileTreeFromClosure() {
        TestFile baseDir = expectPathResolved('base')

        when:
        def fileTree = fileOperations.fileTree { from 'base' }

        then:
        fileTree instanceof FileTree
        fileTree.dir == baseDir
        fileTree.resolver.is(resolver)
    }

    def createsZipFileTree() {
        expectPathResolved('path')
        expectTempFileCreated()
        when:
        def zipTree = fileOperations.zipTree('path')

        then:
        zipTree instanceof FileTreeAdapter
        zipTree.tree instanceof ZipFileTree
    }

    def createsTarFileTree() {
        TestFile file = tmpDir.file('path')
        resolver.resolveResource('path') >> new FileResource(file)

        when:
        def tarTree = fileOperations.tarTree('path')

        then:
        tarTree instanceof FileTreeAdapter
        tarTree.tree instanceof TarFileTree
    }

    def copiesFiles() {
        FileTree fileTree = Mock(FileTree)
        resolver.resolveFilesAsTree(_) >> fileTree
        // todo we should make this work so that we can be more specific
//        resolver.resolveFilesAsTree(['file'] as Object[]) >> fileTree
//        resolver.resolveFilesAsTree(['file'] as Set) >> fileTree
        fileTree.matching(_) >> fileTree
        resolver.resolve('dir') >> tmpDir.getTestDirectory()

        when:
        def result = fileOperations.copy { from 'file'; into 'dir' }

        then:
        result instanceof CopyActionImpl
        !result.didWork
    }

    def deletes() {
        TestFile fileToBeDeleted = tmpDir.file("file")
        ConfigurableFileCollection fileCollection = new DefaultConfigurableFileCollection(resolver, null, "file")
        resolver.resolveFiles(["file"] as Object[]) >> fileCollection
        resolver.resolve("file") >> fileToBeDeleted
        fileToBeDeleted.touch();

        expect:
        fileOperations.delete('file') == true
        fileToBeDeleted.isFile() == false
    }

    def makesDir() {
        TestFile dirToBeCreated = tmpDir.file("parentDir", "dir")
        resolver.resolve('parentDir/dir') >> dirToBeCreated

        when:
        File actualDir = fileOperations.mkdir('parentDir/dir')

        then:
        actualDir == dirToBeCreated
        actualDir.isDirectory() == true
    }

    def makesDirThrowsExceptionIfPathPointsToFile() {
        TestFile dirToBeCreated = tmpDir.file("parentDir", "dir")
        dirToBeCreated.touch();
        resolver.resolve('parentDir/dir') >> dirToBeCreated

        when:
        fileOperations.mkdir('parentDir/dir')

        then:
        thrown(InvalidUserDataException)
    }

    def createsCopySpec() {
        when:
        def spec = fileOperations.copySpec { include 'pattern'}

        then:
        spec instanceof CopySpecImpl
        spec.includes == ['pattern'] as Set
    }

    private TestFile expectPathResolved(String path) {
        TestFile file = tmpDir.file(path)
        resolver.resolve(path) >> file
        return file
    }

    private URI expectPathResolvedToUri(String path) {
        TestFile file = tmpDir.file(path)
        resolver.resolveUri(path) >> file.toURI()
        return file.toURI()
    }

    private TestFile expectTempFileCreated() {
        TestFile file = tmpDir.file('expandedArchives')
        temporaryFileProvider.newTemporaryFile('expandedArchives') >> file
        return file
    }

    def javaexec() {
        File testFile = tmpDir.file("someFile")
        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)
        List files = ClasspathUtil.getClasspath(getClass().classLoader)

        when:
        ExecResult result = fileOperations.javaexec {
            classpath(files as Object[])
            main = 'org.gradle.api.internal.file.SomeMain'
            args testFile.absolutePath
        }

        then:
        testFile.isFile()
        result.exitValue == 0
    }

    def javaexecWithNonZeroExitValueShouldThrowException() {
        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)

        when:
        fileOperations.javaexec {
            main = 'org.gradle.UnknownMain'
        }

        then:
        thrown(ExecException)
    }

    def javaexecWithNonZeroExitValueAndIgnoreExitValueShouldNotThrowException() {
        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)

        when:
        ExecResult result = fileOperations.javaexec {
            main = 'org.gradle.UnknownMain'
            ignoreExitValue = true
        }

        then:
        result.exitValue != 0
    }

    def exec() {
        if (OperatingSystem.current().isWindows()) {
            return
        }

        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)
        File testFile = tmpDir.file("someFile")

        when:
        ExecResult result = fileOperations.exec {
            executable = "touch"
            workingDir = tmpDir.getTestDirectory()
            args testFile.name
        }

        then:
        testFile.isFile()
        result.exitValue == 0
    }

    def execWithNonZeroExitValueShouldThrowException() {
        if (OperatingSystem.current().isWindows()) {
            return
        }
        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)

        when:
        fileOperations.exec {
            executable = "touch"
            workingDir = tmpDir.getTestDirectory()
            args tmpDir.testDirectory.name + "/nonExistingDir/someFile"
        }

        then:
        thrown(ExecException)
    }

    def execWithNonZeroExitValueAndIgnoreExitValueShouldNotThrowException() {
        if (OperatingSystem.current().isWindows()) {
            return
        }
        fileOperations = new DefaultFileOperations(resolver(), taskResolver, temporaryFileProvider, instantiator)

        when:
        ExecResult result = fileOperations.exec {
            ignoreExitValue = true
            executable = "touch"
            workingDir = tmpDir.getTestDirectory()
            args tmpDir.testDirectory.name + "/nonExistingDir/someFile"
        }

        then:
        result.exitValue != 0
    }

    def resolver() {
        return TestFiles.resolver(tmpDir.testDirectory)
    }
}

class SomeMain {
    static void main(String[] args) {
        FileUtils.touch(new File(args[0]))
    }
}
