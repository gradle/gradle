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

package org.gradle.nativeplatform.toolchain.internal.gcc.metadata

import org.gradle.api.Transformer
import org.gradle.api.provider.Property
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.os.OperatingSystem
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Issue

import java.util.regex.Matcher

import static org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccCompilerType.CLANG
import static org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccCompilerType.GCC

@UsesNativeServices
class GccMetadataProviderTest extends Specification {
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
    static def gccMingw = """#define __GNUC_MINOR__ 0
#define __GNUC_PATCHLEVEL__ 0
#define __GNUC__ 10
"""

    static def gccAmd64 = """#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __amd64__ 1
"""
    static def gccCygwin64 = """#define __CYGWIN__ 1
#define __GNUC__ 7
#define __GNUC_MINOR__ 3
#define __x86_64__ 1
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

    static def clangOnLinux = """#define __GNUC_MINOR__ 2
#define __GNUC_MINOR__ 2
#define __GNUC_PATCHLEVEL__ 1
#define __GNUC__ 4
#define __VERSION__ "4.2.1 Compatible Ubuntu Clang 3.6.0 (tags/RELEASE_360/final)"
#define __clang__ 1
#define __clang_major__ 3
#define __clang_minor__ 6
#define __clang_patchlevel__ 0
#define __clang_version__ "3.6.0 (tags/RELEASE_360/final)
"""

    private static String gccVerboseOutput(String versionNumber = '4.2.1', List<String> includes = []) {
        """Using built-in specs.
COLLECT_GCC=gcc
Target: x86_64-linux-gnu
Configured with: ../src/configure -v --with-pkgversion='Ubuntu ${versionNumber}-2ubuntu1~14.04.3' --with-bugurl=file:///usr/share/doc/gcc-4.8/README.Bugs --enable-languages=c,c++,java,go,d,fortran,objc,obj-c++ --prefix=/usr --host=x86_64-linux-gnu --target=x86_64-linux-gnu
Thread model: posix
gcc version ${versionNumber} (Ubuntu ${versionNumber}-2ubuntu1~14.04.3)
COLLECT_GCC_OPTIONS='-E' '-v' '-mtune=generic' '-march=x86-64'
 /usr/lib/gcc/x86_64-linux-gnu/4.8/cc1 -E -quiet -v -imultiarch x86_64-linux-gnu - -mtune=generic -march=x86-64 -fstack-protector -Wformat -Wformat-security
ignoring nonexistent directory "/usr/local/include/x86_64-linux-gnu"
ignoring nonexistent directory "/usr/lib/gcc/x86_64-linux-gnu/4.8/../../../../x86_64-linux-gnu/include"
#include "..." search starts here:
#include <...> search starts here:
${includes.collect { " ${it}" }.join('\n')}
End of search list.
"""
    }

    private static String clangVerboseOutput(String version = '5.0', includes = [], frameworks = []) {
        def versionWithoutDots = version.replaceAll(/\./, '')
        """Apple LLVM version ${version} (clang-${versionWithoutDots}0.0.38)
Target: x86_64-apple-darwin16.7.0
Thread model: posix
InstalledDir: /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin
 "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang" -cc1 -triple x86_64-apple-macosx10.12.0 -E -fcolor-diagnostics -dM -o - -x c -
clang -cc1 version ${version}.0 (clang-${versionWithoutDots}0.0.38) default target x86_64-apple-darwin16.7.0
#include "..." search starts here:
#include <...> search starts here:
${includes.collect { " ${it}" }.join('\n')}
${frameworks.collect { " ${it} (framework directory)" }.join('\n')}
 /System/Library/Frameworks (framework directory)
 /Library/Frameworks (framework directory)
End of search list.
"""
    }

    private static String clangOnLinuxVerboseOutput = """Ubuntu clang version 3.6.0-2ubuntu1~trusty1 (tags/RELEASE_360/final) (based on LLVM 3.6.0)
Target: x86_64-pc-linux-gnu
Thread model: posix
Found candidate GCC installation: /usr/bin/../lib/gcc/i686-linux-gnu/4.8
Found candidate GCC installation: /usr/bin/../lib/gcc/i686-linux-gnu/4.8.4
Found candidate GCC installation: /usr/bin/../lib/gcc/i686-linux-gnu/4.9
Found candidate GCC installation: /usr/bin/../lib/gcc/i686-linux-gnu/4.9.3
Found candidate GCC installation: /usr/bin/../lib/gcc/x86_64-linux-gnu/4.8
Found candidate GCC installation: /usr/bin/../lib/gcc/x86_64-linux-gnu/4.8.4
Found candidate GCC installation: /usr/bin/../lib/gcc/x86_64-linux-gnu/4.9
Found candidate GCC installation: /usr/bin/../lib/gcc/x86_64-linux-gnu/4.9.3
Found candidate GCC installation: /usr/lib/gcc/i686-linux-gnu/4.8
Found candidate GCC installation: /usr/lib/gcc/i686-linux-gnu/4.8.4
Found candidate GCC installation: /usr/lib/gcc/i686-linux-gnu/4.9
Found candidate GCC installation: /usr/lib/gcc/i686-linux-gnu/4.9.3
Found candidate GCC installation: /usr/lib/gcc/x86_64-linux-gnu/4.8
Found candidate GCC installation: /usr/lib/gcc/x86_64-linux-gnu/4.8.4
Found candidate GCC installation: /usr/lib/gcc/x86_64-linux-gnu/4.9
Found candidate GCC installation: /usr/lib/gcc/x86_64-linux-gnu/4.9.3
Selected GCC installation: /usr/bin/../lib/gcc/x86_64-linux-gnu/4.8
Candidate multilib: .;@m64
Candidate multilib: 32;@m32
Candidate multilib: x32;@mx32
Selected multilib: .;@m64
 "/usr/lib/llvm-3.6/bin/clang" -cc1 -triple x86_64-pc-linux-gnu -E -disable-free -disable-llvm-verifier -main-file-name - -mrelocation-model static -mthread-model posix -mdisable-fp-elim -fmath-errno -masm-verbose -mconstructor-aliases -munwind-tables -fuse-init-array -target-cpu x86-64 -target-linker-version 2.24 -v -dwarf-column-info -resource-dir /usr/lib/llvm-3.6/bin/../lib/clang/3.6.0 -internal-isystem /usr/local/include -internal-isystem /usr/lib/llvm-3.6/bin/../lib/clang/3.6.0/include -internal-externc-isystem /usr/bin/../lib/gcc/x86_64-linux-gnu/4.8/include -internal-externc-isystem /usr/include/x86_64-linux-gnu -internal-externc-isystem /include -internal-externc-isystem /usr/include -fdebug-compilation-dir /home/vmadmin -ferror-limit 19 -fmessage-length 173 -mstackrealign -fobjc-runtime=gcc -fdiagnostics-show-option -fcolor-diagnostics -dM -o - -x c -
clang -cc1 version 3.6.0 based upon LLVM 3.6.0 default target x86_64-pc-linux-gnu
ignoring nonexistent directory "/include"
#include "..." search starts here:
#include <...> search starts here:
 /usr/local/include
 /usr/lib/llvm-3.6/bin/../lib/clang/3.6.0/include
 /usr/bin/../lib/gcc/x86_64-linux-gnu/4.8/include
 /usr/include/x86_64-linux-gnu
 /usr/include
End of search list.
"""

    private static String gccMinGWOnLinuxOutput = """Using built-in specs.
COLLECT_GCC=x86_64-w64-mingw32-gcc
Target: x86_64-w64-mingw32
Configured with: ../../src/configure --build=x86_64-linux-gnu --prefix=/usr --includedir='/usr/include' --mandir='/usr/share/man' --infodir='/usr/share/info' --sysconfdir=/etc --localstatedir=/var --disable-option-checking --disable-silent-rules --libdir='/usr/lib/x86_64-linux-gnu' --libexecdir='/usr/lib/x86_64-linux-gnu' --disable-maintainer-mode --disable-dependency-tracking --prefix=/usr --enable-shared --enable-static --disable-multilib --with-system-zlib --libexecdir=/usr/lib --without-included-gettext --libdir=/usr/lib --enable-libstdcxx-time=yes --with-tune=generic --with-headers --enable-version-specific-runtime-libs --enable-fully-dynamic-string --enable-libgomp --enable-languages=c,c++,fortran,objc,obj-c++,ada --enable-lto --enable-threads=win32 --program-suffix=-win32 --program-prefix=x86_64-w64-mingw32- --target=x86_64-w64-mingw32 --with-as=/usr/bin/x86_64-w64-mingw32-as --with-ld=/usr/bin/x86_64-w64-mingw32-ld --enable-libatomic --enable-libstdcxx-filesystem-ts=yes --enable-dependency-tracking
Thread model: win32
Supported LTO compression algorithms: zlib
gcc version 10-win32 20210110 (GCC)
COLLECT_GCC_OPTIONS='-dM' '-E' '-v' '-mtune=generic' '-march=x86-64'
 /usr/lib/gcc/x86_64-w64-mingw32/10-win32/cc1 -E -quiet -v -U_REENTRANT - -mtune=generic -march=x86-64 -dM
ignoring nonexistent directory "/usr/lib/gcc/x86_64-w64-mingw32/10-win32/../../../../x86_64-w64-mingw32/sys-include"
#include "..." search starts here:
#include <...> search starts here:
 /usr/lib/gcc/x86_64-w64-mingw32/10-win32/include
 /usr/lib/gcc/x86_64-w64-mingw32/10-win32/include-fixed
 /usr/lib/gcc/x86_64-w64-mingw32/10-win32/../../../../x86_64-w64-mingw32/include
End of search list."""

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def execActionFactory = Mock(ExecActionFactory)

    def "can scrape version number from output of GCC #versionNumber"() {
        expect:
        def result = output(gcc, gccVerboseOutput(versionNumber.toString()))
        result.available
        result.component.version == VersionNumber.parse(versionNumber)

        where:
        gcc          | versionNumber
        gccMajorOnly | "3.0.0"
        gccNoMinor   | "3.0.4"
        gcc3         | "3.3.4"
        gcc4         | "4.2.1"
    }

    def "can scrape version from output of GCC"() {
        expect:
        def result = output(gcc4, gccVerboseOutput('4.2.1', []))
        result.available
        result.component.vendor == 'gcc version 4.2.1 (Ubuntu 4.2.1-2ubuntu1~14.04.3)'
    }

    def "can scrape architecture from GCC output"() {
        expect:
        def x86 = output(gccX86, gccVerboseOutput())
        x86.component.defaultArchitecture.isI386()

        def amd64 = output(gccAmd64, gccVerboseOutput())
        amd64.component.defaultArchitecture.isAmd64()
    }

    @Issue("https://github.com/gradle/gradle-native/issues/1107")
    def "can handle mingw GCC on Linux, despite the version number not being fully included"() {
        expect:
        def result = output(gccMingw, gccMinGWOnLinuxOutput)
        result.available
    }

    def "handles output that cannot be parsed"() {
        def visitor = Mock(DiagnosticsVisitor)

        expect:
        def result = output(out)
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC metadata: g++ produced unexpected output.")

        where:
        out << ["not sure about this", ""]
    }

    def "yields broken result when version number in stdout and stderr do not match up"() {
        def visitor = Mock(DiagnosticsVisitor)

        expect:
        def result = output(gcc4, gccVerboseOutput('4.8'))
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC metadata: could not find vendor in output of g++.")
    }

    def "handles failure to execute g++"() {
        given:
        def visitor = Mock(DiagnosticsVisitor)
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)

        and:
        def metadataProvider = GccMetadataProvider.forGcc(execActionFactory)
        def binary = new File("g++")

        when:
        def result = metadataProvider.getCompilerMetaData([]) { it.executable(binary) }

        then:
        1 * execActionFactory.newExecAction() >> action
        1 * action.getStandardOutput() >> _
        1 * action.getErrorOutput() >> _
        1 * action.getIgnoreExitValue() >> _
        1 * action.execute() >> execResult
        1 * execResult.getExitValue() >> 1

        and:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC metadata: failed to execute g++ -dM -E -v -.")
    }

    def "can scrape ok output for clang"() {
        expect:
        def result = output stdin, stderr, CLANG
        result.available
        result.component.version == VersionNumber.parse(version)
        result.component.vendor == vendor

        where:
        stdin        | stderr                    | version | vendor
        clang        | clangVerboseOutput()      | "5.0.0" | 'Apple LLVM version 5.0 (clang-500.0.38)'
        clangOnLinux | clangOnLinuxVerboseOutput | '3.6.0' | 'Ubuntu clang version 3.6.0-2ubuntu1~trusty1 (tags/RELEASE_360/final) (based on LLVM 3.6.0)'
    }

    def "detects clang pretending to be gcc"() {
        def visitor = Mock(DiagnosticsVisitor)

        expect:
        def result = output clang
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("XCode g++ is a wrapper around Clang. Treating it as Clang and not GCC.")
    }

    def "detects gcc pretending to be clang"() {
        def visitor = Mock(DiagnosticsVisitor)

        expect:
        def result = output gcc4, CLANG
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("g++ appears to be GCC rather than Clang. Treating it as GCC.")
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "parses gcc system includes"() {
        def includes = correctPathSeparators(['/usr/local', '/usr/some/dir'])
        expect:
        def result = output(gcc4, gccVerboseOutput('4.2.1', includes), GCC)
        result.component.systemIncludes*.path == includes
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "parses clang system includes"() {
        def includes = correctPathSeparators([
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang/9.0.0/include',
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include',
            '/usr/include'
        ])
        def frameworks = correctPathSeparators(['/System/Library/Frameworks', '/Library/Frameworks'])
        expect:
        def result = output(clang, clangVerboseOutput('5.0', includes, frameworks), CLANG)
        result.component.systemIncludes*.path == includes
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "ignores Framework directories for GCC"() {
        def includes = [
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang/9.0.0/include',
            '/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include',
            '/usr/include'
        ]
        def frameworkDirs = ['/System/Library/Frameworks', '/Library/Frameworks']
        expect:
        def result = output(gcc4, gccVerboseOutput('4.2.1', includes + frameworkDirs), GCC)
        result.component.systemIncludes*.path == includes
    }

    @Requires(UnitTestPreconditions.Windows)
    def "parses gcc cygwin system includes and maps to windows paths"() {
        def includes = [
            '/usr/include',
            '/usr/local/include'
        ]
        def mapped = [
            'C:\\cygwin\\usr\\include',
            'C:\\cygwin\\usr\\local\\include'
        ]
        def binDir = tmpDir.createDir('bin')
        def cygpath = binDir.createFile(OperatingSystem.current().getExecutableName('cygpath'))

        expect:
        runsCompiler(gccCygwin64, gccVerboseOutput('7.3', includes))
        mapsPath(cygpath, '/usr/include', 'C:\\cygwin\\usr\\include')
        mapsPath(cygpath, '/usr/local/include', 'C:\\cygwin\\usr\\local\\include')
        def provider = new GccMetadataProvider(execActionFactory, GCC)
        def result = provider.getCompilerMetaData([binDir]) { it.executable(new File("gcc")) }
        result.component.systemIncludes*.path == mapped
    }

    def correctPathSeparators(Collection<String> paths) {
        paths.collect { it.replaceAll('/', Matcher.quoteReplacement(File.separator)) }
    }

    SearchResult<GccMetadata> output(String outputStr, GccCompilerType compilerType = GCC) {
        output(outputStr, "", compilerType)
    }

    SearchResult<GccMetadata> output(String output, String error, GccCompilerType compilerType = GCC, List<File> path = []) {
        runsCompiler(output, error)
        def provider = new GccMetadataProvider(execActionFactory, compilerType)
        provider.getCompilerMetaData(path) { it.executable(new File("g++")) }
    }

    void runsCompiler(String output, String error) {
        def action = Mock(ExecAction)
        def errorOutput = Mock(Property)
        def standardOutput = Mock(Property)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.getStandardOutput() >> standardOutput
        1 * action.getErrorOutput() >> errorOutput
        1 * action.getIgnoreExitValue() >> _
        1 * standardOutput.set(_) >> { OutputStream outstr -> outstr << output }
        1 * errorOutput.set(_) >> { OutputStream errorstr -> errorstr << error }
        1 * action.execute() >> result
    }

    void mapsPath(TestFile cygpath, String from, String to) {
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)
        def standardOutput = Mock(Property)
        1 * execActionFactory.newExecAction() >> action
        1 * action.commandLine(cygpath.absolutePath, '-w', from)
        1 * action.getStandardOutput() >> standardOutput
        1 * action.getErrorOutput() >> _
        1 * standardOutput.set(_) >> { OutputStream outputStream -> outputStream.write(to.bytes) }
        1 * action.execute() >> execResult
        _ * execResult.assertNormalExitValue()
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
