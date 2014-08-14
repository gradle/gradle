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

package org.gradle.api.internal.artifacts.metadata

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import spock.lang.Specification

class ComponentMetadataAdapterTest extends Specification {
    def "provides access to read only attributes of metadata details"() {
        def ComponentMetadataDetails details = Stub(ComponentMetadataDetails) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> { "1.0" }
                getGroup() >> { "org.group" }
                getName() >> { "api" }
            }
            isChanging() >> true
            getStatus() >> "testing"
            getStatusScheme() >> [ "testing", "production" ]
        }
        def ComponentMetadataAdapter metadataAdapter = new ComponentMetadataAdapter(details)

        expect:
        metadataAdapter.changing
        metadataAdapter.id.version == "1.0"
        metadataAdapter.id.group == "org.group"
        metadataAdapter.id.name == "api"
        metadataAdapter.status == "testing"
        metadataAdapter.statusScheme == [ "testing", "production" ]
    }
}
