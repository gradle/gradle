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
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.FileSource
import org.junit.platform.engine.support.descriptor.MethodSource
import spock.lang.Specification

/**
 * Unit tests for {@link ClassMethodNameFilter}, covering the descriptor shapes that integration tests
 * cannot reach: sources other than class and method, and leaves with no enclosing class at all.
 */
class ClassMethodNameFilterTest extends Specification {
    def "descriptors with children are included so their leaves can decide"() {
        given:
        def parent = container('ExcludedTest', ClassSource.from('ExcludedTest'))
        parent.addChild(leaf('aTest', MethodSource.from('ExcludedTest', 'aTest')))

        expect:
        includes(parent, excludes: ['ExcludedTest'])
    }

    def "method based leaves are matched by class and method name"() {
        expect:
        includes(leaf('aTest()', MethodSource.from('SomeTest', 'aTest')), commandLine: ['SomeTest.aTest'])
        excludes(leaf('aTest()', MethodSource.from('SomeTest', 'aTest')), commandLine: ['SomeTest.otherTest'])
    }

    def "leaves with no source are matched by enclosing class and reporting name"() {
        given:
        def rule = { -> underClass('ArchRulesTest', leaf('firstRule', null)) }

        expect:
        includes(rule(), commandLine: ['ArchRulesTest'])
        includes(rule(), commandLine: ['ArchRulesTest.firstRule'])
        excludes(rule(), commandLine: ['ArchRulesTest.secondRule'])
        excludes(rule(), commandLine: ['OtherTest'])
        excludes(rule(), excludes: ['*firstRule'])
    }

    def "leaves with a custom source are matched the same way as leaves with no source"() {
        given:
        def rule = { -> underClass('ArchRulesTest', leaf('firstRule', new CustomSource())) }

        expect:
        includes(rule(), commandLine: ['ArchRulesTest.firstRule'])
        excludes(rule(), commandLine: ['ArchRulesTest.secondRule'])
    }

    def "leaves with no enclosing class anywhere above them are included"() {
        given:
        def orphan = { -> container('root', null).with { it.addChild(leaf('someTest', null)); it.children.first() } }

        expect:
        includes(orphan(), commandLine: ['SomethingElse'])
        includes(orphan(), excludes: ['*someTest'])
    }

    def "file based leaves outside any class are included"() {
        expect:
        includes(leaf('foo.feature', FileSource.from(new File('foo.feature'))), commandLine: ['SomethingElse'])
    }

    def "file based leaves under a class are filtered by that class"() {
        given:
        def scenario = { -> underClass('CucumberEntryPoint', leaf('a scenario', FileSource.from(new File('foo.feature')))) }

        expect: "the enclosing class selects them, as it does for any other non-method test"
        includes(scenario(), commandLine: ['CucumberEntryPoint'])
        includes(scenario(), commandLine: ['CucumberEntryPoint.a scenario'])

        and: "a filter naming an unrelated class no longer lets them through"
        excludes(scenario(), commandLine: ['SomeOtherTest'])
        excludes(scenario(), excludes: ['CucumberEntryPoint'])
    }

    private boolean includes(Map<String, List<String>> filter, TestDescriptor descriptor) {
        assert apply(filter, descriptor)
        true
    }

    private boolean excludes(Map<String, List<String>> filter, TestDescriptor descriptor) {
        assert !apply(filter, descriptor)
        true
    }

    private boolean apply(Map<String, List<String>> filter, TestDescriptor descriptor) {
        def spec = new TestFilterSpec(
            (filter.includes ?: []) as Set,
            (filter.excludes ?: []) as Set,
            (filter.commandLine ?: []) as Set
        )
        return new ClassMethodNameFilter(new TestSelectionMatcher(spec)).apply(descriptor).included()
    }

    /** Puts {@code child} under a class-sourced container, as engines do for non-method tests. */
    private static TestDescriptor underClass(String className, TestDescriptor child) {
        container(className, ClassSource.from(className)).addChild(child)
        return child
    }

    private static TestDescriptorFixture container(String name, TestSource source) {
        return new TestDescriptorFixture(name, source, TestDescriptor.Type.CONTAINER)
    }

    private static TestDescriptorFixture leaf(String name, TestSource source) {
        return new TestDescriptorFixture(name, source, TestDescriptor.Type.TEST)
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

    private static class CustomSource implements TestSource {}
}
