/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.watch.registry.impl

import com.google.common.collect.ImmutableSet
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.BiConsumer

class DefaultFileWatcherProbeRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "can register probes for unknown filestores"() {
        given:
        def registry = new DefaultFileWatcherProbeRegistry()
        def probeDirectory = file("probe")

        when:
        def hierarchy = file("hierarchy")
        then:
        // file store can't be found for non-existent path
        //noinspection GroovyAccessibility
        DefaultFileWatcherProbeRegistry.getFileStore(hierarchy.toPath()) == null

        when:
        registry.registerProbe(hierarchy, probeDirectory)
        registry.updateProbedHierarchies(ImmutableSet.of(hierarchy), noop, noop)
        then:
        registry.unprovenHierarchies().count() == 1

        when:
        registry.triggerWatchProbe(hierarchy.toPath())
        then:
        registry.unprovenHierarchies().count() == 0
    }

    TestFile file(Object... path) {
        temporaryFolder.testDirectory.file(path)
    }

    BiConsumer<File, Boolean> noop = { p, s -> }
}
