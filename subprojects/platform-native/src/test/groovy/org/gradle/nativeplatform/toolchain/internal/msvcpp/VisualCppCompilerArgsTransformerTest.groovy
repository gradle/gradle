/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import spock.lang.Specification

class VisualCppCompilerArgsTransformerTest extends Specification {
    def transformer = new VisualCppCompilerArgsTransformer<NativeCompileSpec>() {
        @Override
        protected String getLanguageOption() {
            return "/GRADLE_DSL"
        }
    }

    def "includes options when debug and optimized enabled"() {
        def spec = Stub(NativeCompileSpec)
        spec.debuggable >> debug
        spec.optimized >> optimize

        expect:
        def args = transformer.transform(spec)
        args.containsAll(expected)

        where:
        debug | optimize | expected
        true  | false    | ["/Zi"]
        false | true     | ["/O2"]
        true  | true     | ["/Zi", "/O2"]
    }

}
