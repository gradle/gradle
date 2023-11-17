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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.internal.provider.PropertySpec
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.internal.state.ManagedFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class FileCollectionPropertySpec<T extends FileCollection> extends PropertySpec<T> {
    def taskResolver = Mock(TaskResolver)
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def fileResolver = TestFiles.resolver(tmpDir.testDirectory)
    def fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)

    def factory = new DefaultFilePropertyFactory(host, fileResolver, fileCollectionFactory)
    def patternSetFactory = TestFiles.patternSetFactory
    def taskDependencyFactory = DefaultTaskDependencyFactory.forProject(taskResolver, (tasks) -> { })
    def FileCollection value0 = newCollection().from("file.txt")
    def FileCollection value1 = newCollection().from("file1.txt")
    def FileCollection value2 = newCollection().from("file2.txt")
    def FileCollection value3 = newCollection().from("file3.txt")

    protected DefaultConfigurableFileCollection newCollection() {
        new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host)
    }

    Class<FileCollection> type() {
        return ConfigurableFileCollection.class
    }

    @Override
    FileCollection someValue() {
        return value0
    }

    @Override
    FileCollection someOtherValue() {
        return value1
    }

    @Override
    FileCollection someOtherValue2() {
        return value2
    }

    @Override
    FileCollection someOtherValue3() {
        return value3
    }

    @Override
    PropertyInternal<Directory> propertyWithNoValue() {
        return factory.newFileCollectionProperty(ConfigurableFileCollection.class)
    }

    @Override
    PropertyInternal<T> providerWithNoValue() {
        return super.providerWithNoValue()
    }

    @Override
    ManagedFactory managedFactory() {
        new ManagedFactories.FileCollectionPropertyFactory(factory)
    }
}
