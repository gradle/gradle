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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.resources.DefaultResourceHandler
import org.gradle.cache.internal.TestCaches
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

@UsesNativeServices
class DefaultFileOperationsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @TempDir
    File cacheDir

    private final FileResolver resolver = Mock() {
        getPatternSetFactory() >> TestFiles.getPatternSetFactory()
    }
    private final TemporaryFileProvider temporaryFileProvider = new DefaultTemporaryFileProvider(() -> cacheDir);
    private final Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private final DefaultDirectoryFileTreeFactory directoryFileTreeFactory = Mock()
    private final FileHasher fileHasher = Mock()
    private final DefaultResourceHandler.Factory resourceHandlerFactory = Mock()
    private final FileCollectionFactory fileCollectionFactory = Mock()
    private DefaultFileOperations fileOperations = instance()

    private DefaultFileOperations instance(FileResolver resolver = resolver) {
        instantiator.newInstance(
            DefaultFileOperations,
            resolver,
            instantiator,
            directoryFileTreeFactory,
            fileHasher,
            resourceHandlerFactory,
            fileCollectionFactory,
            TestUtil.objectFactory(),
            TestFiles.fileSystem(),
            TestFiles.patternSetFactory,
            TestFiles.deleter(),
            TestFiles.documentationRegistry(),
            TestFiles.taskDependencyFactory(),
            TestUtil.providerFactory(),
            TestCaches.decompressionCacheFactory(temporaryFileProvider.newTemporaryDirectory("cache")),
            null
        )
    }

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

    def usesFactoryToCreateConfigurableFileCollection() {
        def fileCollection = Mock(ConfigurableFileCollection)

        when:
        def result = fileOperations.configurableFiles('a', 'b', 'c')

        then:
        result == fileCollection
        1 * fileCollectionFactory.configurableFiles() >> fileCollection
        1 * fileCollection.from('a', 'b', 'c') >> fileCollection
    }

    def usesFactoryToCreateImmutableFiles() {
        def fileCollection = Mock(FileCollectionInternal)

        when:
        def result = fileOperations.immutableFiles('a', 'b', 'c')

        then:
        result == fileCollection
        1 * fileCollectionFactory.resolving(['a', 'b', 'c'] as Object[]) >> fileCollection
    }

    def createsFileTree() {
        def tree = Mock(ConfigurableFileTree)

        when:
        def fileTree = fileOperations.fileTree('base')

        then:
        fileTree.is(tree)
        1 * fileCollectionFactory.fileTree() >> tree
        1 * tree.from('base')
    }

    def createsFileTreeFromMap() {
        def tree = Mock(ConfigurableFileTree)

        when:
        def fileTree = fileOperations.fileTree(dir: 'base')

        then:
        fileTree.is(tree)
        1 * fileCollectionFactory.fileTree() >> tree
        1 * tree.setDir('base')
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
        resolver.resolve('path') >> file

        when:
        def tarTree = fileOperations.tarTree('path')

        then:
        tarTree instanceof FileTreeAdapter
        tarTree.tree instanceof TarFileTree
    }

    def copiesFiles() {
        def fileCollection = Mock(ConfigurableFileCollection)
        def fileTree = Mock(FileTreeInternal)
        fileCollectionFactory.configurableFiles() >> fileCollection
        fileCollection.asFileTree >> fileTree
        fileTree.matching(_) >> fileTree
        resolver.resolve('dir') >> tmpDir.getTestDirectory()

        when:
        def result = fileOperations.copy { from 'file'; into 'dir' }

        then:
        !result.didWork
    }

    def deletes() {
        def fileToBeDeleted = tmpDir.file("file")
        def fileCollection = Stub(FileCollectionInternal)
        fileCollectionFactory.resolving(["file"] as Object[]) >> fileCollection
        fileCollection.iterator() >> [fileToBeDeleted].iterator()
        fileToBeDeleted.touch()

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
        def spec = fileOperations.copySpec { include 'pattern' }

        then:
        spec instanceof DefaultCopySpec
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

    def resolver() {
        return TestFiles.resolver(tmpDir.testDirectory)
    }
}
