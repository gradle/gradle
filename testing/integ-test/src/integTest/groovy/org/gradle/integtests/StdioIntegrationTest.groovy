/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.TextUtil

class StdioIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireDaemon()
        // This isn't actually required - the test is fine with shared daemons
        // In fact, it would be much better to test this feature using shared daemons
        executer.requireIsolatedDaemons()
    }

    def "task can read stdin when stdin has bounded length"() {
        given:
        buildFile << '''
task echo {
    doLast {
        def reader = new BufferedReader(new InputStreamReader(System.in))
        while (true) {
            def line = reader.readLine() // readline will chomp the newline off the end
            if (!line) {
                break
            }
            print "[$line]"
        }
    }
}
'''
        executer.withStdinPipe(new PipedOutputStream() {
            @Override
            void connect(PipedInputStream snk) throws IOException {
                super.connect(snk)
                write(TextUtil.toPlatformLineSeparators("abc\n123").bytes)
                close()
            }
        })

        when:
        run "echo"

        then:
        output.contains("[abc][123]")
    }

    def "task can read stdin when stdin has unbounded length"() {
        given:
        buildFile << '''
task echo {
    doLast {
        def reader = new BufferedReader(new InputStreamReader(System.in))
        while (true) {
            def line = reader.readLine() // readline will chomp the newline off the end
            assert line != null
            if (line == 'close') {
                break
            }
            print "[$line]"
        }
    }
}
'''
        when:
        executer.withStdinPipe(new PipedOutputStream() {
            @Override
            void connect(PipedInputStream snk) throws IOException {
                super.connect(snk)
                write(TextUtil.toPlatformLineSeparators("abc\n123\nclose\nmore-stuff").bytes)
            }
        })
        run "echo"

        then:
        output.contains("[abc][123]")
    }
}
