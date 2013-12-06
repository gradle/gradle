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

package org.gradle.nativebinaries.toolchain.internal.gcc.version

import org.gradle.api.Transformer
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.TreeVisitor
import spock.lang.Specification
import spock.lang.Unroll

class GccVersionDeterminerTest extends Specification {
    def execActionFactory = Mock(ExecActionFactory)

    @Unroll
    "can scrape ok output for #version"() {
        expect:
        def result = output(output)
        result.available
        result.version == version

        where:
        [version, output] << OUTPUTS.collect { [it.value, it.key] }
    }

    def "handles gcc output that cannot be parsed"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output(output)
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC version: g++ produced unexpected output.")

        where:
        output << [ "not sure about this", "" ]
    }

    def "g++ -v execution error ok"() {
        given:
        def visitor = Mock(TreeVisitor)
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)

        and:
        def determiner = new GccVersionDeterminer(execActionFactory)
        def binary = new File("g++")
        
        when:
        def result = determiner.transform(binary)
        
        then:
        1 * execActionFactory.newExecAction() >> action
        1 * action.execute() >> execResult
        1 * execResult.getExitValue() >> 1

        and:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine GCC version: failed to execute g++ -v.")
    }

    def "detects clang pretending to be gcc"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output """Configured with: --prefix=/Applications/Xcode.app/Contents/Developer/usr --with-gxx-include-dir=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/include/c++/4.2.1
        Apple LLVM version 5.0 (clang-500.2.79) (based on LLVM 3.3svn)
        Target: x86_64-apple-darwin13.0.0
        Thread model: posix
        """
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("XCode g++ is a wrapper around Clang. Treating it as Clang and not GCC.")
    }

    GccVersionResult output(String output) {
        def action = Mock(ExecAction)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.setErrorOutput(_) >> { OutputStream outstr -> outstr << output; action }
        1 * action.execute() >> result
        new GccVersionDeterminer(execActionFactory).transform(new File("g++"))
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

    static final OUTPUTS = [
            """Using built-in specs.
Target: i686-apple-darwin11
Configured with: /private/var/tmp/llvmgcc42/llvmgcc42-2336.1~22/src/configure
Thread model: posix
gcc version 4.2.1 (Based on Apple Inc. build 5658) (LLVM build 2336.1.00)""": "4.2.1",
            """Reading specs from /opt/gcc/3.4.6/usr/local/bin/../lib/gcc/i686-pc-linux-gnu/3.4.6/specs
Configured with: /home/ld/Downloads/gcc-3.4.6/configure
Thread model: posix
gcc version 3.4.6""": "3.4.6",
            """Reading specs from /opt/gcc/3.4.6/usr/local/bin/../lib/gcc/i686-pc-linux-gnu/3.4.6/specs
Configured with: /home/ld/Downloads/gcc-3.4.6/configure
Thread model: posix
gcc version 3.4.6-sometag""": "3.4.6-sometag"
    ]
}
