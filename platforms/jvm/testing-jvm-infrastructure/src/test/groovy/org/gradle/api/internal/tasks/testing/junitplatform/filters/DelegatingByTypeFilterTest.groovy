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

import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.FileSource
import org.junit.platform.launcher.PostDiscoveryFilter
import spock.lang.Specification

/**
 * Unit tests for {@link DelegatingByTypeFilter}'s routing contract. Every descriptor reaches exactly one
 * delegate; nothing is waved through without an opinion.
 */
class DelegatingByTypeFilterTest extends Specification {
    def defaultDelegate = recordingFilter()
    def fileDelegate = recordingFilter()

    def filter = new DelegatingByTypeFilter(defaultDelegate).tap {
        addDelegate(FileSource, fileDelegate)
    }

    def "a source with a registered delegate goes to that delegate"() {
        when:
        filter.apply(descriptor(FileSource.from(new File('foo.feature'))))

        then:
        fileDelegate.seen == 1
        defaultDelegate.seen == 0
    }

    def "a source with no registered delegate goes to the default delegate"() {
        when:
        filter.apply(descriptor(ClassSource.from('SomeTest')))

        then:
        defaultDelegate.seen == 1
        fileDelegate.seen == 0
    }

    def "an absent source goes to the default delegate"() {
        when:
        filter.apply(descriptor(null))

        then:
        defaultDelegate.seen == 1
        fileDelegate.seen == 0
    }

    def "an unregistered source is filtered, not silently included"() {
        given:
        def excludeEverything = new DelegatingByTypeFilter({ FilterResult.excluded('nope') } as PostDiscoveryFilter)

        expect:
        excludeEverything.apply(descriptor(new CustomSource())).excluded()
        excludeEverything.apply(descriptor(null)).excluded()
    }

    private static TestDescriptor descriptor(TestSource source) {
        return new AbstractTestDescriptor(UniqueId.root('fixture', 'test'), 'test', source) {
            @Override
            TestDescriptor.Type getType() {
                return TestDescriptor.Type.TEST
            }
        }
    }

    private static RecordingFilter recordingFilter() {
        return new RecordingFilter()
    }

    private static class RecordingFilter implements PostDiscoveryFilter {
        int seen

        @Override
        FilterResult apply(TestDescriptor descriptor) {
            seen++
            return FilterResult.included('recorded')
        }
    }

    private static class CustomSource implements TestSource {}
}
