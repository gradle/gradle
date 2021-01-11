/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.internal.resolve

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import spock.lang.Specification
import spock.lang.Unroll

class JvmLibraryResolveContextTest extends Specification {
    private final static String COMPONENT_NAME = 'lib'
    private final static String VARIANT = 'api'

    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()

    @Unroll
    def "context name for project #path and library #library is #usage"() {
        given:
        def id = new DefaultLibraryBinaryIdentifier(path, COMPONENT_NAME, VARIANT)

        when:
        def context = resolveContext(id, usage)

        then:
        context.name == usage.configurationName

        where:
        path       | library  | usage
        ':myPath'  | 'myLib'  | UsageKind.API
        ':myPath'  | 'myLib2' | UsageKind.RUNTIME
        ':myPath2' | 'myLib'  | UsageKind.API
    }

    private JvmLibraryResolveContext resolveContext(DefaultLibraryBinaryIdentifier id, UsageKind usage) {
        new JvmLibraryResolveContext(id, Mock(VariantsMetaData), Collections.emptyList(), usage, 'test source set', moduleIdentifierFactory)
    }

}
