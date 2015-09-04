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

package org.gradle.nativeplatform.toolchain.internal.gcc.version

import org.gradle.api.Transformer
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.TreeVisitor
import org.gradle.util.UsesNativeServices
import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

@UsesNativeServices
class GccVersionDeterminerTest extends Specification {
    def execActionFactory = Mock(ExecActionFactory)
    static def gcc4 = """#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __INTMAX_C(c) c ## LL
#define __REGISTER_PREFIX__ """
    static def gcc3 = """#define __gnu_linux__ 1
#define __GNUC_PATCHLEVEL__ 4
#define __GNUC__ 3
#define __GNUC_MINOR__ 3
"""
    static def gccMajorOnly = """#define __gnu_linux__ 1
#define __GNUC__ 3
"""
    static def gccNoMinor = """#define __gnu_linux__ 1
#define __GNUC__ 3
#define __GNUC_PATCHLEVEL__ 4
"""
    static def gccX86 = """#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __i386__ 1
"""
    static def gccAmd64 = """#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __amd64__ 1
"""
    static def clang = """#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __VERSION__ "4.2.1 Compatible Apple LLVM 5.0 (clang-500.2.79)"
#define __clang__ 1
#define __clang_major__ 5
#define __clang_minor__ 0
#define __clang_patchlevel__ 0
#define __clang_version__ "5.0 (clang-500.2.79)"
"""

    @Unroll
    "can scrape version from output of GCC #version"() {
        expect:
        def result = output(gcc)
        result.available
        result.version == VersionNumber.parse(version)
        !result.clang

        where:
        gcc          | version
        gccMajorOnly | "3.0.0"
        gccNoMinor   | "3.0.4"
        gcc3         | "3.3.4"
        gcc4         | "4.2.1"
    }

    @Unroll
    "can scrape architecture from GCC output"() {
        expect:
        def x86 = output(gccX86)
        x86.defaultArchitecture.isI386()

        def amd64 = output(gccAmd64)
        amd64.defaultArchitecture.isAmd64()
    }

    def "handles output that cannot be parsed"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output(out)
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC version: g++ produced unexpected output.")

        where:
        out << ["not sure about this", ""]
    }

    def "handles failure to execute g++"() {
        given:
        def visitor = Mock(TreeVisitor)
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)

        and:
        def determiner = GccVersionDeterminer.forGcc(execActionFactory)
        def binary = new File("g++")

        when:
        def result = determiner.getGccMetaData(binary, [])

        then:
        1 * execActionFactory.newExecAction() >> action
        1 * action.execute() >> execResult
        1 * execResult.getExitValue() >> 1

        and:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC version: failed to execute g++ -dM -E -.")
    }

    def "can scrape ok output for clang"() {
        expect:
        def result = output clang, true
        result.available
        result.version == VersionNumber.parse("5.0.0")
        result.clang
    }

    def "detects clang pretending to be gcc"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output clang
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("XCode g++ is a wrapper around Clang. Treating it as Clang and not GCC.")
    }

    def "detects gcc pretending to be clang"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output gcc4, true
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("g++ appears to be GCC rather than Clang. Treating it as GCC.")
    }

    GccVersionResult output(String output, boolean clang = false) {
        def action = Mock(ExecAction)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.setStandardOutput(_) >> { OutputStream outstr -> outstr << output; action }
        1 * action.execute() >> result
        new GccVersionDeterminer(execActionFactory, clang).getGccMetaData(new File("g++"), [])
    }

    Transformer transformer(constant) {
        transformer { constant }
    }

    Transformer transformer(Closure closure) {
        new Transformer() {
            String transform(original) {
                closure.call(original)
            }
        }
    }
}
