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


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.runner.RunWith
import org.junit.Test
import org.gradle.api.internal.tasks.TaskResolver
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.api.PathValidation
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.copy.CopyActionImpl
import org.gradle.api.internal.file.copy.CopySpecImpl
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.archive.TarFileTree

@RunWith(JMock.class)
public class DefaultFileOperationsTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final FileResolver resolver = context.mock(FileResolver.class)
    private final TaskResolver taskResolver = context.mock(TaskResolver.class)
    private final TemporaryFileProvider temporaryFileProvider = context.mock(TemporaryFileProvider.class)
    private final DefaultFileOperations fileOperations = new DefaultFileOperations(resolver, taskResolver, temporaryFileProvider)
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()

    @Test
    public void resolvesFile() {
        TestFile file = expectPathResolved('path')
        assertThat(fileOperations.file('path'), equalTo(file))
    }

    @Test
    public void resolvesFileWithValidation() {
        TestFile file = tmpDir.file('path')
        context.checking {
            one(resolver).resolve('path', PathValidation.EXISTS)
            will(returnValue(file))
        }
        assertThat(fileOperations.file('path', PathValidation.EXISTS), equalTo(file))
    }

    @Test
    public void resolvesFiles() {
        def fileCollection = fileOperations.files('a', 'b')
        assertThat(fileCollection, instanceOf(PathResolvingFileCollection.class))
        assertThat(fileCollection.sources, equalTo(['a', 'b']))
        assertThat(fileCollection.resolver, sameInstance(resolver))
        assertThat(fileCollection.buildDependency.resolver, sameInstance(taskResolver))
    }

    @Test
    public void createsFileTree() {
        TestFile baseDir = expectPathResolved('base')

        def fileTree = fileOperations.fileTree('base')
        assertThat(fileTree, instanceOf(FileTree.class))
        assertThat(fileTree.dir, equalTo(baseDir))
        assertThat(fileTree.resolver, sameInstance(resolver))
    }

    @Test
    public void createsFileTreeFromMap() {
        TestFile baseDir = expectPathResolved('base')

        def fileTree = fileOperations.fileTree(dir: 'base')
        assertThat(fileTree, instanceOf(FileTree.class))
        assertThat(fileTree.dir, equalTo(baseDir))
        assertThat(fileTree.resolver, sameInstance(resolver))
    }

    @Test
    public void createsFileTreeFromClosure() {
        TestFile baseDir = expectPathResolved('base')
        
        def fileTree = fileOperations.fileTree { from 'base' }
        assertThat(fileTree, instanceOf(FileTree.class))
        assertThat(fileTree.dir, equalTo(baseDir))
        assertThat(fileTree.resolver, sameInstance(resolver))
    }

    @Test
    public void createsZipFileTree() {
        expectPathResolved('path')
        expectTempFileCreated()
        def zipTree = fileOperations.zipTree('path')
        assertThat(zipTree, instanceOf(ZipFileTree.class))
    }

    @Test
    public void createsTarFileTree() {
        expectPathResolved('path')
        expectTempFileCreated()
        def tarTree = fileOperations.tarTree('path')
        assertThat(tarTree, instanceOf(TarFileTree.class))
    }

    @Test
    public void copiesFiles() {
        context.checking {
            one(resolver).resolveFilesAsTree(['file'] as Set)
            one(resolver).resolve('dir')
            will(returnValue(tmpDir.getDir()))
        }
        
        def result = fileOperations.copy { from 'file'; into 'dir' }
        assertThat(result, instanceOf(CopyActionImpl.class))
        assertFalse(result.didWork)
    }
    
    @Test
    public void createsCopySpec() {
        def spec = fileOperations.copySpec { include 'pattern'}
        assertThat(spec, instanceOf(CopySpecImpl.class))
        assertThat(spec.includes, equalTo(['pattern'] as Set))
    }

    private TestFile expectPathResolved(String path) {
        TestFile file = tmpDir.file(path)
        context.checking {
            one(resolver).resolve(path)
            will(returnValue(file))
        }
        return file
    }

    private TestFile expectTempFileCreated() {
        TestFile file = tmpDir.file('expandedArchives')
        context.checking {
            one(temporaryFileProvider).newTemporaryFile('expandedArchives')
            will(returnValue(file))
        }
        return file
    }
}
