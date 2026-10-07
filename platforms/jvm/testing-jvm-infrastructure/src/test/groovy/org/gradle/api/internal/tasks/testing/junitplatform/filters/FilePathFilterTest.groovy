/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform.filters

import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.DirectorySource
import org.junit.platform.engine.support.descriptor.FileSource
import org.junit.platform.launcher.PostDiscoveryFilter
import spock.lang.Specification

/**
 * Unit tests for {@link FilePathFilter}, covering which files it judges by path and which it hands on
 * to its fallback.
 */
class FilePathFilterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "files under a definition dir are matched by their path relative to it"() {
        given:
        def root = temp.createDir("definitions")
        def feature = root.file("hello.feature").touch()

        expect:
        includes(leaf(FileSource.from(feature)), root, commandLine: ['hello'])
        excludes(leaf(FileSource.from(feature)), root, commandLine: ['other'])
    }

    def "directories under a definition dir are matched the same way"() {
        given:
        def root = temp.createDir("definitions")
        def sub = root.createDir("sub")

        expect:
        includes(leaf(DirectorySource.from(sub)), root, commandLine: ['sub'])
        excludes(leaf(DirectorySource.from(sub)), root, commandLine: ['other'])
    }

    def "a file under no definition dir is handed to the fallback when a class encloses it"() {
        given:
        def outside = temp.createDir("elsewhere").file("hello.feature").touch()

        when: "the path cannot be matched, so the filter must not rule on it itself"
        def result = apply([commandLine: ['anything']], underClass(leaf(FileSource.from(outside))), temp.createDir("definitions"), NEVER)

        then: "the fallback decided, even though it excludes"
        !result.included()
        result.reason.get() == NEVER_REASON
    }

    def "a file with neither a path nor an enclosing class is excluded without consulting the fallback"() {
        given:
        def outside = temp.createDir("elsewhere").file("hello.feature").touch()

        when: "nothing names this test, so an including fallback must not be allowed to run it"
        def result = apply([commandLine: ['anything']], leaf(FileSource.from(outside)), temp.createDir("definitions"), ALWAYS)

        then:
        !result.included()
        result.reason.get() != ALWAYS_REASON
    }

    def "a file under no definition dir is matched by its enclosing class when that is the fallback"() {
        given:
        def outside = temp.createDir("elsewhere").file("hello.feature").touch()
        def definitions = temp.createDir("definitions")
        def scenario = { ->
            def child = leaf(FileSource.from(outside))
            container(ClassSource.from('RunCukesTest')).addChild(child)
            return child
        }

        expect: "the suite class that encloses the scenario selects and excludes it"
        includes(scenario(), definitions, commandLine: ['RunCukesTest'])
        excludes(scenario(), definitions, commandLine: ['SomeOtherTest'])
        excludes(scenario(), definitions, excludes: ['RunCukesTest'])
    }

    def "every file is handed to the fallback when there are no definition dirs"() {
        given:
        def feature = temp.createDir("features").file("hello.feature").touch()

        when:
        def result = apply([commandLine: ['hello']], underClass(leaf(FileSource.from(feature))), null, NEVER)

        then: "not matched by path, even though the path would have matched under a definition dir"
        !result.included()
        result.reason.get() == NEVER_REASON
    }

    def "a missing file is handed to the fallback rather than matched by path"() {
        given:
        def root = temp.createDir("definitions")
        def missing = root.file("hello.feature")

        when: "its real path cannot be read, so it cannot be placed under the root"
        def result = apply([commandLine: ['hello']], underClass(leaf(FileSource.from(missing))), root, NEVER)

        then: "the fallback decides, rather than the filter claiming a match it cannot substantiate"
        !result.included()
        result.reason.get() == NEVER_REASON
    }

    def "a descriptor with no source is rejected"() {
        when:
        apply([commandLine: ['anything']], leaf(null), temp.createDir("definitions"), NEVER)

        then:
        thrown(IllegalArgumentException)
    }

    def "a descriptor whose source is neither file nor directory based is rejected"() {
        when:
        apply([commandLine: ['anything']], leaf(ClassSource.from('SomeTest')), temp.createDir("definitions"), NEVER)

        then:
        thrown(IllegalArgumentException)
    }

    private static final String NEVER_REASON = "fallback consulted"
    private static final String ALWAYS_REASON = "fallback consulted and included"

    /** A fallback that always excludes, so that consulting it is distinguishable from matching a path. */
    private static final PostDiscoveryFilter NEVER = { TestDescriptor it -> FilterResult.excluded(NEVER_REASON) }

    /** A fallback that always includes, so that NOT consulting it is distinguishable from consulting it. */
    private static final PostDiscoveryFilter ALWAYS = { TestDescriptor it -> FilterResult.included(ALWAYS_REASON) }

    /** Puts a class-sourced container above the descriptor, as an engine does for a suite entry point. */
    private static TestDescriptor underClass(TestDescriptor descriptor) {
        container(ClassSource.from('RunCukesTest')).addChild(descriptor)
        return descriptor
    }

    private boolean includes(Map<String, List<String>> filter, TestDescriptor descriptor, File root) {
        assert applyWithClassFallback(filter, descriptor, root).included()
        true
    }

    private boolean excludes(Map<String, List<String>> filter, TestDescriptor descriptor, File root) {
        assert !applyWithClassFallback(filter, descriptor, root).included()
        true
    }

    private FilterResult applyWithClassFallback(Map<String, List<String>> filter, TestDescriptor descriptor, File root) {
        def matcher = matcher(filter, root)
        return new FilePathFilter(matcher, new ClassMethodNameFilter(matcher)).apply(descriptor)
    }

    private static FilterResult apply(Map<String, List<String>> filter, TestDescriptor descriptor, File root, PostDiscoveryFilter fallback) {
        return new FilePathFilter(matcher(filter, root), fallback).apply(descriptor)
    }

    private static TestSelectionMatcher matcher(Map<String, List<String>> filter, File root) {
        def spec = new TestFilterSpec(
            (filter.includes ?: []) as Set,
            (filter.excludes ?: []) as Set,
            (filter.commandLine ?: []) as Set
        )
        def roots = root == null ? [] : [root.toPath().toRealPath()]
        return new TestSelectionMatcher(spec, roots)
    }

    private static TestDescriptorFixture container(TestSource source) {
        return new TestDescriptorFixture('container', source, TestDescriptor.Type.CONTAINER)
    }

    private static TestDescriptorFixture leaf(TestSource source) {
        return new TestDescriptorFixture('leaf', source, TestDescriptor.Type.TEST)
    }

    private static class TestDescriptorFixture extends AbstractTestDescriptor {
        private final TestDescriptor.Type type

        TestDescriptorFixture(String name, TestSource source, TestDescriptor.Type type) {
            super(UniqueId.root('fixture', name), name, source)
            this.type = type
        }

        @Override
        TestDescriptor.Type getType() {
            return type
        }
    }
}
