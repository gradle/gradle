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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TreeVisitor
import org.gradle.util.UsesNativeServices
import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Matcher

import static org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProvider.CompilerType.CLANG
import static org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProvider.CompilerType.GCC

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

    private static String gccVerboseOutput(includes) {
        """Using built-in specs.
COLLECT_GCC=gcc
Target: x86_64-linux-gnu
Configured with: ../src/configure -v --with-pkgversion='Ubuntu 4.8.4-2ubuntu1~14.04.3' --with-bugurl=file:///usr/share/doc/gcc-4.8/README.Bugs --enable-languages=c,c++,java,go,d,fortran,objc,obj-c++ --prefix=/usr --host=x86_64-linux-gnu --target=x86_64-linux-gnu
Thread model: posix
gcc version 4.8.4 (Ubuntu 4.8.4-2ubuntu1~14.04.3)
COLLECT_GCC_OPTIONS='-E' '-v' '-mtune=generic' '-march=x86-64'
 /usr/lib/gcc/x86_64-linux-gnu/4.8/cc1 -E -quiet -v -imultiarch x86_64-linux-gnu - -mtune=generic -march=x86-64 -fstack-protector -Wformat -Wformat-security
ignoring nonexistent directory "/usr/local/include/x86_64-linux-gnu"
ignoring nonexistent directory "/usr/lib/gcc/x86_64-linux-gnu/4.8/../../../../x86_64-linux-gnu/include"
#include "..." search starts here:
#include <...> search starts here:
${includes.collect { " ${it}" }.join('\n') }
End of search list.
"""
    }

    private static String clangVerboseOutput(includes, frameworks) {
        """Apple LLVM version 9.0.0 (clang-900.0.38)
Target: x86_64-apple-darwin16.7.0
Thread model: posix
InstalledDir: /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin
 "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang" -cc1 -triple x86_64-apple-macosx10.12.0 -E -fcolor-diagnostics -dM -o - -x c -
clang -cc1 version 9.0.0 (clang-900.0.38) default target x86_64-apple-darwin16.7.0
#include "..." search starts here:
#include <...> search starts here:
${includes.collect { " ${it}" }.join('\n') }
${frameworks.collect { " ${it} (framework directory)" }.join('\n') }
 /System/Library/Frameworks (framework directory)
 /Library/Frameworks (framework directory)
End of search list.
"""
    }

    @Unroll
    "can scrape version from output of GCC #version"() {
        expect:
        def result = output(gcc)
        result.available
        result.version == VersionNumber.parse(version)

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
        1 * visitor.node("Could not determine GCC version: failed to execute g++ -dM -E -v -.")
    }

    def "can scrape ok output for clang"() {
        expect:
        def result = output clang, CLANG
        result.available
        result.version == VersionNumber.parse("5.0.0")
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
        def result = output gcc4, CLANG
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("g++ appears to be GCC rather than Clang. Treating it as GCC.")
    }

    def "parses gcc system includes"() {
        def includes = correctPathSeparators(['/usr/local', '/usr/some/dir'])
        expect:
        def result = output gcc4, gccVerboseOutput(includes), GCC
        result.systemIncludes*.path == includes
    }

    def "parses clang system includes"() {
        def includes = correctPathSeparators([
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang/9.0.0/include',
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include',
            '/usr/include'
        ])
        def frameworks = correctPathSeparators(['/System/Library/Frameworks', '/Library/Frameworks'])
        expect:
        def result = output clang, clangVerboseOutput(includes, frameworks), CLANG
        result.systemIncludes*.path == includes
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "ignores Framework directories for GCC"() {
        def includes = [
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang/9.0.0/include',
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include',
            '/usr/include'
        ]
        def frameworkDirs = ['/System/Library/Frameworks', '/Library/Frameworks']
        expect:
        def result = output gcc4, gccVerboseOutput(includes + frameworkDirs), GCC
        result.systemIncludes*.path == includes
    }

    def correctPathSeparators(Collection<String> paths) {
        paths.collect { it.replaceAll('/', Matcher.quoteReplacement(File.separator)) }
    }

    GccVersionResult output(String outputStr, CompilerMetaDataProvider.CompilerType compilerType = GCC) {
        output(outputStr, "", compilerType)
    }

    GccVersionResult output(String output, String error, CompilerMetaDataProvider.CompilerType compilerType = GCC) {
        def action = Mock(ExecAction)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.setStandardOutput(_) >> { OutputStream outstr -> outstr << output; action }
        1 * action.setErrorOutput(_) >> { OutputStream errorstr -> errorstr << error; action }
        1 * action.execute() >> result
        new GccVersionDeterminer(execActionFactory, compilerType).getGccMetaData(new File("g++"), [])
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
