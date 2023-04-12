/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.file.archive

import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.file.MaybeCompressedFileResource
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.api.provider.Provider
import org.gradle.api.resources.internal.LocalResourceAdapter
import org.gradle.cache.internal.TestCaches
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TestUtil
import spock.lang.Issue

import java.nio.file.Files
import java.nio.file.StandardCopyOption

import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory
import static org.gradle.api.internal.file.TestFiles.fileHasher
import static org.gradle.api.internal.file.TestFiles.fileSystem
import static org.gradle.util.internal.WrapUtil.toList

class TarFileTreeSoakTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/23391")
    def "can decompress TAR archive with truncated header"() {
        given:
        TestFile tgz = temporaryFolder.file("test.tgz")

        try (InputStream input = new URL("https://github.com/sass/dart-sass/releases/download/1.57.1/dart-sass-1.57.1-linux-x64.tar.gz").openStream()) {
            Files.copy(input, tgz.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        when:
        MaybeCompressedFileResource resource = new MaybeCompressedFileResource(new LocalResourceAdapter(TestFiles.fileRepository().localResource(tgz)))
        TarFileTree tree = new TarFileTree(asProvider(tgz), asProvider(resource), fileSystem(), directoryFileTreeFactory(), fileHasher(), TestCaches.decompressionCache(temporaryFolder.createDir("cache-dir")))
        def files = getFiles(tree)

        then:
        files.containsAll(toList("sass", "LICENSE"))
    }

    private static Set<String> getFiles(TarFileTree fileTree) {
        final Set<String> files = new LinkedHashSet<>()
        fileTree.visit(new EmptyFileVisitor() {
            @Override
            void visitFile(FileVisitDetails fileDetails) {
                files.add(fileDetails.getName())
            }
        })
        return files
    }

    private static <T> Provider<T> asProvider(T object) {
        return TestUtil.providerFactory().provider(() -> object)
    }
}
