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

package org.gradle.nativebinaries.toolchain.internal.gcc

import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec
import spock.lang.Specification

class ShortCircuitArgsTransformerTest extends Specification {

    def "can short circuit args transformation"(){
        given:
        def spec = Mock(NativeCompileSpec)
        def expectedArgs = Arrays.asList("some", "options");
        def delegateArgsTransformer = Mock(ArgsTransformer)
        def ShortCircuitArgsTransformer transformer = new ShortCircuitArgsTransformer(delegateArgsTransformer)

        when:
        def transformedArgs = transformer.transform(spec)
        then:
        transformedArgs == expectedArgs
        1 * delegateArgsTransformer.transform(spec) >> expectedArgs

        when:
        transformedArgs = transformer.transform(spec)
        then:
        transformedArgs == expectedArgs
        0 * delegateArgsTransformer.transform(spec)

    }

    def "does not short circuit when spec has changed"(){
        given:
        def spec = Mock(NativeCompileSpec)
        def changedSpec = Mock(NativeCompileSpec)
        def delegateArgsTransformer = Mock(ArgsTransformer)
        def ShortCircuitArgsTransformer transformer = new ShortCircuitArgsTransformer(delegateArgsTransformer)

        when:
        transformer.transform(spec)
        then:
        1 * delegateArgsTransformer.transform(spec)

        when:
        transformer.transform(changedSpec)
        then:
        1 * delegateArgsTransformer.transform(changedSpec)
        0 * delegateArgsTransformer.transform(spec)
    }
}
