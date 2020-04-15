/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.file.Directory
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.internal.state.ManagedFactory

class DirectoryPropertyTest extends FileSystemPropertySpec<Directory> {
    @Override
    Class<Directory> type() {
        return Directory.class
    }

    @Override
    Directory someValue() {
        return baseDir.dir("dir1").get()
    }

    @Override
    Directory someOtherValue() {
        return baseDir.dir("other1").get()
    }

    @Override
    Directory someOtherValue2() {
        return baseDir.dir("other2").get()
    }

    @Override
    Directory someOtherValue3() {
        return baseDir.dir("other3").get()
    }

    @Override
    PropertyInternal<Directory> propertyWithNoValue() {
        return factory.newDirectoryProperty()
    }

    @Override
    PropertyInternal<Directory> propertyWithDefaultValue() {
        return factory.newDirectoryProperty()
    }

    @Override
    ManagedFactory managedFactory() {
        new ManagedFactories.DirectoryPropertyManagedFactory(factory)
    }

    def "can view directory as a file tree"() {
        given:
        def dir1 = tmpDir.createDir("dir1")
        def file1 = dir1.createFile("sub-dir/file1")
        def file2 = dir1.createFile("file2")
        def dir2 = tmpDir.createDir("dir2")
        def file3 = dir2.createFile("other/file3")

        expect:
        def tree1 = baseDir.asFileTree
        tree1.files == [file1, file2, file3] as Set

        and:
        def tree2 = baseDir.dir("dir2").get().asFileTree
        tree2.files == [file3] as Set
    }

    def "can view relative paths as a file collection"() {
        given:
        def fileCollection = baseDir.files("a/b/c", "d", "e/f")

        expect:
        fileCollection.files == [
            tmpDir.file("a/b/c"),
            tmpDir.file("d"),
            tmpDir.file("e/f")
        ] as Set
    }
}
