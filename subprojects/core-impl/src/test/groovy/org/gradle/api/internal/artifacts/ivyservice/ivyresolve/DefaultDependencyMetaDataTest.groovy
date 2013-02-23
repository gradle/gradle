/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

class DefaultDependencyMetaDataTest extends Specification {
    final DependencyDescriptor descriptor = Mock()
    final DefaultDependencyMetaData metaData = new DefaultDependencyMetaData(descriptor)

    def setup() {
        _ * descriptor.getDependencyRevisionId() >> ModuleRevisionId.newInstance("org", "module", "1.2+")
    }

    def "constructs selector from descriptor"() {
        expect:
        metaData.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.2+")
    }

    def "creates a copy with new requested version"() {
        DependencyDescriptor descriptorCopy = Mock()

        given:

        when:
        def copy = metaData.withRequestedVersion("1.3+")

        then:
        1 * descriptor.clone(ModuleRevisionId.newInstance("org", "module", "1.3+")) >> descriptorCopy

        and:
        copy.descriptor == descriptorCopy
    }
}
