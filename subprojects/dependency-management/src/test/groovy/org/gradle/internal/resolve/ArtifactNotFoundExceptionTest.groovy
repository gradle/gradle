/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resolve

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class ArtifactNotFoundExceptionTest extends Specification {
    def "formats message and locations"() {
        def artifactId = Stub(ComponentArtifactIdentifier) {
            getDisplayName() >> "<artifact>"
        }
        def locations = ["http://somewhere", "sftp://elsewhere"]
        def exception = new ArtifactNotFoundException(artifactId, locations)

        expect:
        exception.message == TextUtil.toPlatformLineSeparators("""Could not find <artifact>.
Searched in the following locations:
    http://somewhere
    sftp://elsewhere""")
    }

    def "formats message when no locations attempted"() {
        def artifactId = Stub(ComponentArtifactIdentifier) {
            getDisplayName() >> "<artifact>"
        }
        def exception = new ArtifactNotFoundException(artifactId, [])

        expect:
        exception.message == "Could not find <artifact>."
    }
}
