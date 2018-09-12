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

package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.MetadataProvider
import spock.lang.Specification

class DefaultComponentSelectionTest extends Specification {
    DefaultComponentSelection selection
    MetadataProvider metadataProvider = Mock()

    def setup() {
        selection = new DefaultComponentSelection(Stub(ModuleComponentIdentifier), metadataProvider)
    }

    def "accepted by default"() {
        expect:
        !selection.rejected
        selection.rejectionReason == null
    }

    def "accepted until rejected"() {
        when:
        selection.reject("bad")

        then:
        selection.rejected
        selection.rejectionReason == "bad"
    }

    def "last rejection wins"() {
        when:
        selection.reject("bad")
        selection.reject("worse")

        then:
        selection.rejected
        selection.rejectionReason == "worse"
    }

    def 'delegates to metadata provider for metadata access'() {
        when:
        selection.getMetadata()

        then:
        1 * metadataProvider.componentMetadata
    }

    def 'delegates to metadata provider for ivy module descriptor access'() {
        when:
        selection.getDescriptor(IvyModuleDescriptor.class)

        then:
        1 * metadataProvider.ivyModuleDescriptor
    }

    def 'ignores non ivy descriptor requests'() {
        when:
        selection.getDescriptor(Object.class)

        then:
        0 * metadataProvider._
    }
}
