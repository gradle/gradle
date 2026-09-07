/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provenance.Attribution
import org.gradle.api.internal.provenance.ContributorKey
import org.gradle.api.internal.provenance.DiagnosticOrigin
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.internal.state.ModelObject

/** Runs the ordinary scalar contract, including producer and lifecycle checks, with provenance enabled. */
class AttributedDefaultPropertyTest extends DefaultPropertyTest {
    private int propertyId

    @Override
    DefaultProperty propertyWithDefaultValue(Class type) {
        def readHost = host
        def scope = new ScopeIdentity('build', ':project')
        def identity = "property-${propertyId++}".toString()
        def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.UNKNOWN, 'unknown'),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, 'unknown', 'unknown'), scope, null)
        new AttributedProperty(new PropertyProvenanceHost() {
            @Override
            ScopeIdentity getOwnerScope() { scope }

            @Override
            String newOccurrenceScope() { identity }

            @Override
            Attribution currentAttribution() { attribution }

            @Override
            String beforeRead(ModelObject producer) { readHost.beforeRead(producer) }
        }, type)
    }
}
