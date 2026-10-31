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

package org.gradle.internal.watch.registry.impl

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Function

@CleanupTestDirectory
class DefaultFileWatcherProbeRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def hierarchy = temporaryFolder.testDirectory.file("hierarchy").createDir()
    def probeDirectory = hierarchy.file(".gradle")
    def registry = new DefaultFileWatcherProbeRegistry(
        { File it -> new File(new File(it, ".gradle"), "file-system.probe") } as Function<File, File>)

    def probeFiles() {
        (probeDirectory.list() ?: [] as String[])
            .findAll { it.startsWith("file-system") }
            .sort()
    }

    def "re-arming keeps a base name that contains a hyphen"() {
        given:
        registry.registerProbe(hierarchy)

        when:
        registry.rearmWatchProbe(hierarchy)

        then: "the generation is a suffix, not a replacement for everything after the last hyphen"
        probeFiles() == ["file-system-1.probe"]
    }

    def "each re-arming leaves exactly one probe file behind"() {
        given:
        registry.registerProbe(hierarchy)

        when:
        5.times { registry.rearmWatchProbe(hierarchy) }

        then:
        probeFiles() == ["file-system-5.probe"]
    }

    def "a probe file left by an earlier daemon is swept"() {
        given:
        registry.registerProbe(hierarchy)
        probeDirectory.createDir().file("file-system-97.probe").text = "left by a crashed daemon"

        when:
        registry.rearmWatchProbe(hierarchy)

        then:
        probeFiles() == ["file-system-1.probe"]
    }

    def "an unrelated file in the probe directory is not swept"() {
        given:
        registry.registerProbe(hierarchy)
        // The second name shares the probe's prefix, which is what a prefix-only sweep deletes.
        probeDirectory.createDir().file("configuration-cache").text = "not ours"
        probeDirectory.file("file-system-cache.bin").text = "not ours either"
        // A suffix that is not a generation: only digits after the hyphen make one.
        probeDirectory.file("file-system-x.probe").text = "not a generation"

        when:
        registry.rearmWatchProbe(hierarchy)

        then:
        probeDirectory.file("configuration-cache").exists()
        probeDirectory.file("file-system-cache.bin").exists()
        probeDirectory.file("file-system-x.probe").exists()
    }

    def "a probe file of any generation is recognized, live or superseded"() {
        given:
        registry.registerProbe(hierarchy)
        registry.armWatchProbe(hierarchy)

        when:
        registry.rearmWatchProbe(hierarchy)

        then:
        registry.isProbeFile(probeDirectory.file("file-system-1.probe").absolutePath)
        registry.isProbeFile(probeDirectory.file("file-system.probe").absolutePath)
        registry.isProbeFile(probeDirectory.file("file-system-97.probe").absolutePath)

        and:
        !registry.isProbeFile(probeDirectory.file("file-system-cache.bin").absolutePath)
        !registry.isProbeFile(probeDirectory.file("file-system-x.probe").absolutePath)
        !registry.isProbeFile(probeDirectory.file("configuration-cache").absolutePath)
        !registry.isProbeFile(hierarchy.file("file-system-1.probe").absolutePath)
    }

    def "the probe directory is left in place when the probe file is removed"() {
        given:
        registry.registerProbe(hierarchy)
        registry.armWatchProbe(hierarchy)

        when:
        registry.removeProbeFiles()

        then: "arming re-creates it every build, so removing it here would churn a watched location"
        !probeDirectory.file("file-system.probe").exists()
        probeDirectory.isDirectory()
    }

    def "a probe directory is recognized whichever hierarchy registered it"() {
        given:
        def nested = hierarchy.createDir("nested")
        registry.registerProbe(hierarchy)
        registry.registerProbe(nested)

        expect: "the outer registry answers for the inner hierarchy's probe directory too"
        registry.isProbeDirectory(probeDirectory.absolutePath)
        registry.isProbeDirectory(nested.file(".gradle").absolutePath)

        and: "and not for a .gradle belonging to no registered hierarchy"
        !registry.isProbeDirectory(temporaryFolder.testDirectory.file("elsewhere/.gradle").absolutePath)
        !registry.isProbeDirectory(hierarchy.absolutePath)
    }

    def "an event for a superseded generation does not prove the hierarchy"() {
        given:
        registry.registerProbe(hierarchy)
        registry.armWatchProbe(hierarchy)
        def supersededPath = probeDirectory.file("file-system.probe").absolutePath

        when: "the event for the previous generation arrives after the probe was re-armed"
        registry.rearmWatchProbe(hierarchy)
        registry.triggerWatchProbe(supersededPath)

        then:
        registry.hasUnprovenHierarchies()

        when: "the event for the current generation arrives"
        registry.triggerWatchProbe(probeDirectory.file("file-system-1.probe").absolutePath)

        then:
        !registry.hasUnprovenHierarchies()
    }
}
