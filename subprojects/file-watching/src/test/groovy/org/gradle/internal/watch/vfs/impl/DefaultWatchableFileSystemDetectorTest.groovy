/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl

import net.rubygrapefruit.platform.file.FileSystemInfo
import net.rubygrapefruit.platform.file.FileSystems
import net.rubygrapefruit.platform.internal.DefaultFileSystemInfo
import spock.lang.Specification

import java.nio.file.Paths
import java.util.stream.Collectors

class DefaultWatchableFileSystemDetectorTest extends Specification {
    def fileSystemsService = Stub(FileSystems)
    def detector = new DefaultWatchableFileSystemDetector(fileSystemsService)

    def "handles no file systems"() {
        fileSystemsService.fileSystems >> []

        expect:
        detect() == []
    }

    def "handles all supported"() {
        fileSystemsService.fileSystems >> [
            fs("/dev/supported", "/", "ext4"),
            fs("/dev/more-supported", "/mnt/supported", "ext4"),
        ]

        expect:
        detect() == []
    }

    def "handles matching #description"() {
        fileSystemsService.fileSystems >> [
            fs("/dev/supported", "/", "ext4"),
            unsupported
        ]

        expect:
        detect() == [unsupported.mountPoint]

        where:
        description   | unsupported
        "unsupported" | fs("/dev/unsupported", "/mnt/unsupported", "UNSUPPORTED")
        "remote"      | fs("/dev/remote", "/mnt/unsupported", "ext4", true)
    }

    private List<FileSystemInfo> detect() {
        detector.detectUnsupportedFileSystems()
            .collect(Collectors.toList())
    }

    private static FileSystemInfo fs(String deviceName, String mountPoint, String type, boolean remote = false) {
        return new DefaultFileSystemInfo(Paths.get(mountPoint).toFile(), type, deviceName, remote, null)
    }
}
