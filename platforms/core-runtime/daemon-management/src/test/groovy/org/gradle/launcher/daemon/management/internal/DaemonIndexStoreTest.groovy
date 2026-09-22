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

package org.gradle.launcher.daemon.management.internal

import org.gradle.launcher.daemon.management.DaemonIndexEntry
import spock.lang.Specification
import spock.lang.TempDir

class DaemonIndexStoreTest extends Specification {

    @TempDir
    File baseDir

    def store = null

    def setup() {
        store = new DaemonIndexStore(baseDir)
    }

    def "an entry survives a round trip"() {
        given:
        def entry = entry("uid-1", 4711L)

        when:
        store.store(entry)

        then:
        store.all == [entry]
    }

    def "an entry replaces the previous entry for the same daemon"() {
        when:
        store.store(entry("uid-1", 1L))
        store.store(entry("uid-1", 2L))

        then:
        store.all.size() == 1
        store.all[0].pid == 2L
    }

    def "entries of several daemons are all reported"() {
        when:
        store.store(entry("uid-1", 1L))
        store.store(entry("uid-2", 2L))

        then:
        store.all*.uid.toSet() == ["uid-1", "uid-2"].toSet()
    }

    def "removing an entry that is not there is not an error"() {
        when:
        store.remove("never-existed")

        then:
        noExceptionThrown()
        store.all.empty
    }

    def "an entry is readable by its owner alone"() {
        given:
        store.store(entry("uid-1", 1L))
        def file = new File(store.indexDir, "uid-1.json")

        expect:
        // An entry carries the token that authorises stopping the daemon, so it must not be world
        // readable. Windows has no such permissions and the calls there are no-ops, as they are for the
        // per-version registry.
        windows || filePermissions(file) == "rw-------"
        windows || filePermissions(store.indexDir) == "rwx------"
    }

    private static boolean isWindows() {
        System.getProperty("os.name").toLowerCase().contains("windows")
    }

    def "members a reader does not know are ignored"() {
        given:
        store.indexDir.mkdirs()
        def members = DaemonIndexStore.toMap(entry("uid-1", 4711L))
        members.put("somethingAddedLater", "a value from a newer daemon")
        new File(store.indexDir, "uid-1.json").text = Json.writeObject(members)

        expect:
        store.all.size() == 1
        store.all[0].pid == 4711L
    }

    def "an entry written to a newer schema is refused rather than guessed at"() {
        given:
        store.indexDir.mkdirs()
        def members = DaemonIndexStore.toMap(entry("uid-1", 4711L))
        members.put("schemaVersion", DaemonIndexEntry.CURRENT_SCHEMA_VERSION + 1)
        new File(store.indexDir, "uid-1.json").text = Json.writeObject(members)

        expect:
        store.all.empty
    }

    def "unreadable entries are skipped and do not hide the readable ones"() {
        given:
        store.store(entry("uid-1", 1L))
        store.indexDir.mkdirs()
        new File(store.indexDir, "broken.json").text = "this is not json"

        expect:
        store.all*.uid == ["uid-1"]
    }

    def "files that are not entries are ignored"() {
        given:
        store.store(entry("uid-1", 1L))
        new File(store.indexDir, "notes.txt").text = "ignore me"

        expect:
        store.all.size() == 1
    }

    def "no entries are reported when the index has never been written"() {
        expect:
        new DaemonIndexStore(new File(baseDir, "empty")).all.empty
    }

    private static String filePermissions(File file) {
        java.nio.file.attribute.PosixFilePermissions.toString(
            java.nio.file.Files.getPosixFilePermissions(file.toPath()))
    }

    private static DaemonIndexEntry entry(String uid, Long pid) {
        new DaemonIndexEntry(
            DaemonIndexEntry.CURRENT_SCHEMA_VERSION,
            "9.9.0",
            uid,
            pid,
            5000,
            ["127.0.0.1", "::1"],
            [1, 2, 3] as byte[],
            1758528000000L,
            "/home/user/.gradle/daemon",
            "/opt/jdk",
            "/home/user/.gradle/daemon/9.9.0/daemon-${pid}.out.log".toString()
        )
    }
}
