/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.internal.resolve
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.language.base.internal.model.VariantsMetaData
import spock.lang.Specification
import spock.lang.Unroll

class LocalComponentResolveContextTest extends Specification {
    private final static String COMPONENT_NAME = 'lib'
    private final static String VARIANT = 'api'

    @Unroll
    def "context name for project #path and library #library is #contextName"() {
        given:
        def id = new DefaultLibraryBinaryIdentifier(path, COMPONENT_NAME, VARIANT)

        when:
        def context = resolveContext(id, contextName)

        then:
        context.name == contextName

        where:
        path       | library  | contextName
        ':myPath'  | 'myLib'  | 'API'
        ':myPath'  | 'myLib2' | 'runtime'
        ':myPath2' | 'myLib'  | 'API'
    }

    private LocalComponentResolveContext resolveContext(DefaultLibraryBinaryIdentifier id, String usage) {
        new LocalComponentResolveContext(id, Mock(VariantsMetaData), Collections.emptyList(), usage, 'test source set')
    }

}
