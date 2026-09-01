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

package org.gradle.internal.build

import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.util.Matchers
import org.gradle.util.Path
import spock.lang.Specification

class BuildIdentityTest extends Specification {

    // This display name format is what BuildState.toString() and BuildState.getDisplayName() produce.
    // Changing it affects user-visible output in logs and error messages.

    def "root build has display name with root path"() {
        def identity = new BuildIdentity(Path.ROOT)

        expect:
        identity.buildPath == Path.ROOT
        identity.displayName == "build ':'"
        identity.toString() == "build ':'"
    }

    def "nested build has display name with its build path"() {
        def identity = new BuildIdentity(Path.path(":included"))

        expect:
        identity.buildPath == Path.path(":included")
        identity.displayName == "build ':included'"
        identity.toString() == "build ':included'"
    }

    def "exposes a build identifier for the same path"() {
        def identity = new BuildIdentity(Path.path(":included:nested"))

        expect:
        identity.buildIdentifier.buildPath == ":included:nested"
    }

    def "rejects a relative build path"() {
        when:
        new BuildIdentity(Path.path("relative"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Build path must be absolute: relative"
    }

    def "has equals"() {
        expect:
        def identity = new BuildIdentity(Path.path(":one"))
        def same = new BuildIdentity(Path.path(":one"))
        def different = new BuildIdentity(Path.path(":two"))

        Matchers.strictlyEquals(identity, same)
        identity != different
    }
}
