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
        protected Optional<String> getLanguageOption() {
            return Optional.of("/GRADLE_DSL")
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

    def "transforms system header and include args correctly"() {
        def spec = Stub(NativeCompileSpec)
        def includes = [ new File("/foo"), new File("/bar") ]
        def systemIncludes = [ new File("/baz") ]
        spec.includeRoots >> includes
        spec.systemIncludeRoots >> systemIncludes

        when:
        def args = transformer.transform(spec)

        then:
        assertHasArguments(args, "/I", includes)
        assertHasArguments(args, "/I", systemIncludes)

        and:
        args.indexOf("/I" + includes.last().absoluteFile.toString()) < args.indexOf("/I" + systemIncludes.first().absoluteFile.toString())
    }

    def "does not include an empty string in compiler options"() {
        given:
        def transformer = new VisualCppCompilerArgsTransformer<NativeCompileSpec>() {}
        def spec = Stub(NativeCompileSpec)

        when:
        def args = transformer.transform(spec)

        then:
        !args.contains("")
    }

    boolean assertHasArguments(List<String> args, String option, List<File> files) {
        files.each { file ->
            assert args.indexOf(option + file.absoluteFile.toString()) > -1
        }
        return true
    }
}
