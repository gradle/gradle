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

import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.provider.PropertySpec
import org.gradle.api.internal.provider.Providers
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

abstract class FileSystemPropertySpec<T extends FileSystemLocation> extends PropertySpec<T> {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def resolver = TestFiles.resolver(tmpDir.testDirectory)
    def fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    def factory = new DefaultFilePropertyFactory(host, resolver, fileCollectionFactory)
    // Make sure that `baseDir` isn't the same as the base for the resolver.
    def baseDir = tmpDir.testDirectory.createDir("base")
    def baseDirectory = factory.newDirectoryProperty().fileValue(baseDir)

    def "can set value using absolute file"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        prop.set(file)

        expect:
        prop.get().asFile == file
    }

    def "can set value using relative file"() {
        given:
        def file = new File("thing")
        def prop = propertyWithNoValue()
        prop.set(file)

        expect:
        prop.get().asFile == tmpDir.file("thing")
    }

    def "can set value using absolute file provider"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        prop.fileProvider(Providers.of(file))

        expect:
        prop.get().asFile == file
    }

    def "can set value using relative file provider"() {
        given:
        def file = new File("thing")
        def prop = propertyWithNoValue()
        prop.fileProvider(Providers.of(file))

        expect:
        prop.get().asFile == tmpDir.file("thing")
    }

    def "cannot set value using file when finalized"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        prop.finalizeValue()

        when:
        prop.set(file)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value using file provider when finalized"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        prop.finalizeValue()

        when:
        prop.fileProvider(Providers.of(file))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value using file when changes disallowed"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        prop.disallowChanges()

        when:
        prop.set(file)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "can query the value of the location of a property"() {
        given:
        def file = tmpDir.file("thing")
        def prop = propertyWithNoValue()
        def location = prop.locationOnly

        expect:
        !location.present
        location.orNull == null

        when:
        prop.fileValue(file)

        then:
        location.present
        location.get().asFile == file
    }

    def "location provider does not have any producer even when the source property does"() {
        given:
        def task = Stub(Task)
        def prop = propertyWithNoValue()
        def location = prop.locationOnly
        prop.attachProducer(owner(task))
        assertHasProducer(prop, task)

        expect:
        assertHasNoProducer(location)
    }

    def "location provider does not check producer when source property is strict"() {
        given:
        def file = tmpDir.file("thing")
        def task = Stub(Task)
        def prop = propertyWithNoValue()
        def location = prop.locationOnly
        prop.fileValue(file)
        prop.attachProducer(owner(task))
        prop.disallowUnsafeRead()

        when:
        def result = location.present

        then:
        result
        1 * host.beforeRead(null) >> null
    }
}
