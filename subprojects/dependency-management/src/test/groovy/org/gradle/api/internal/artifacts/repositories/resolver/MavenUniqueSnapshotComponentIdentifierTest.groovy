/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver

import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class MavenUniqueSnapshotComponentIdentifierTest extends Specification {

    def id = new MavenUniqueSnapshotComponentIdentifier("group", "module", "1.0-SNAPSHOT", "timestamp")

    def "delegates module version to owner"() {
        expect:
        id.group == "group"
        id.module == "module"
        id.version == "1.0-SNAPSHOT"
    }

    def "replaces SNAPSHOT in version with timestamp for timestamped version"() {
        expect:
        id.timestampedVersion == "1.0-timestamp"
    }

    def "has useful display name"() {
        expect:
        id.displayName == "group:module:1.0-SNAPSHOT:timestamp"
    }

    def "can compare with other instance"() {
        when:
        MavenUniqueSnapshotComponentIdentifier id1 = new MavenUniqueSnapshotComponentIdentifier("group", "module", "1.0-SNAPSHOT", "timestamp")
        MavenUniqueSnapshotComponentIdentifier same = new MavenUniqueSnapshotComponentIdentifier("group", "module", "1.0-SNAPSHOT", "timestamp")
        MavenUniqueSnapshotComponentIdentifier differentVersion = new MavenUniqueSnapshotComponentIdentifier("group", "module", "1.1-SNAPSHOT", "timestamp")
        MavenUniqueSnapshotComponentIdentifier differentTimestamp = new MavenUniqueSnapshotComponentIdentifier("group", "module", "1.0-SNAPSHOT", "other")

        then:
        assert strictlyEquals(id1, same)
        assert id1.hashCode() != differentVersion.hashCode()
        assert id1 != differentVersion
        assert id1.hashCode() != differentTimestamp
        assert id1 != differentTimestamp
    }
}
