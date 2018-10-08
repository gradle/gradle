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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class MavenUniqueSnapshotComponentIdentifierTest extends Specification {
    private ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId("group", "module")

    def "delegates module version to owner"() {
        def id = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, version, "timestamp")

        expect:
        id.group == "group"
        id.module == "module"
        id.version == version
        id.timestamp == "timestamp"

        where:
        version         | _
        "1.0-SNAPSHOT"  | _
        "1.0-timestamp" | _
    }

    def "can request timestamp or symbolic version"() {
        def id = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, version, "timestamp")

        expect:
        id.timestampedVersion == "1.0-timestamp"
        id.snapshotVersion == "1.0-SNAPSHOT"

        where:
        version         | _
        "1.0-SNAPSHOT"  | _
        "1.0-timestamp" | _
    }

    def "has useful display name"() {
        def id = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, version, "timestamp")

        expect:
        id.displayName == "group:module:1.0-SNAPSHOT:timestamp"

        where:
        version         | _
        "1.0-SNAPSHOT"  | _
        "1.0-timestamp" | _
    }

    def "can compare with other instance"() {
        when:
        def id1 = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, "1.0-SNAPSHOT", "timestamp")
        def same = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, "1.0-SNAPSHOT", "timestamp")
        def differentVersion = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, "1.1-SNAPSHOT", "timestamp")
        def differentTimestamp = new MavenUniqueSnapshotComponentIdentifier(moduleIdentifier, "1.0-SNAPSHOT", "other")

        then:
        assert strictlyEquals(id1, same)
        assert id1.hashCode() != differentVersion.hashCode()
        assert id1 != differentVersion
        assert id1.hashCode() != differentTimestamp
        assert id1 != differentTimestamp
    }
}
