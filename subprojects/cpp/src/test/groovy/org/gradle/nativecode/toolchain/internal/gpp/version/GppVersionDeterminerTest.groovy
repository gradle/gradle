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

package org.gradle.nativecode.toolchain.internal.gpp.version

import org.gradle.api.Transformer
import org.gradle.internal.Factory
import org.gradle.process.internal.ExecHandleBuilder
import spock.lang.Specification
import spock.lang.Unroll
import org.gradle.process.internal.ExecHandle
import org.gradle.process.ExecResult

class GppVersionDeterminerTest extends Specification {

    @Unroll
    "can scrape ok output"() {
        expect:
        version == output(output)

        where:
        [version, output] << OUTPUTS.collect { [it.value, it.key] }
    }

    def "null output (errored execution) ok"() {
        expect:
        output(null) == null
    }

    def "null scraped ok (can't parse output)"() {
        expect:
        scraped(null) == null
    }

    def "g++ -v execution error ok"() {
        given:
        def builder = Mock(ExecHandleBuilder)
        def handle = Mock(ExecHandle)
        def result = Mock(ExecResult)

        and:
        def determiner = new GppVersionDeterminer(producer(builder), new GppVersionDeterminer.GppVersionOutputScraper())
        def binary = new File("g++")
        
        when:
        String version = determiner.transform(binary)
        
        then:
        1 * builder.build() >> handle
        1 * handle.start() >> handle
        1 * handle.waitForFinish() >> result
        1 * result.getExitValue() >> 1

        and:
        version == null
    }

    def "happy day case"() {
        given:
        def builder = Mock(ExecHandleBuilder)
        def handle = Mock(ExecHandle)
        def result = Mock(ExecResult)
        def output = """Reading specs from /opt/gcc/3.4.6/usr/local/bin/../lib/gcc/i686-pc-linux-gnu/3.4.6/specs
Configured with: /home/ld/Downloads/gcc-3.4.6/configure
Thread model: posix
gcc version 3.4.6"""

        and:
        def determiner = new GppVersionDeterminer(producer(builder), new GppVersionDeterminer.GppVersionOutputScraper())
        def binary = new File("g++")

        when:
        String version = determiner.transform(binary)

        then:
        1 * builder.build() >> handle
        1 * builder.setErrorOutput(_) >> { OutputStream out -> out << output; builder }
        1 * handle.start() >> handle
        1 * handle.waitForFinish() >> result
        1 * result.getExitValue() >> 0

        and:
        version == "3.4.6"
    }

    Transformer<String, File> producer(ExecHandleBuilder builder) {
        new GppVersionDeterminer.GppVersionOutputProducer(new Factory() {
            def create() {
                builder
            }
        })
    }

    String output(String output) {
        new GppVersionDeterminer(transformer(output), new GppVersionDeterminer.GppVersionOutputScraper()).transform(new File("."))
    }

    String scraped(String scraped) {
        new GppVersionDeterminer(transformer("doesntmatter"), transformer(scraped)).transform(new File("."))
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
