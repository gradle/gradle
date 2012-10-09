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

package org.gradle.internal.nativeplatform

import spock.lang.Specification
import net.rubygrapefruit.platform.Terminals
import spock.lang.Unroll

class NativePlatformConsoleDetectorTest extends Specification {

    private Terminals terminals = Mock()

    private NativePlatformConsoleDetector detector = new NativePlatformConsoleDetector(terminals)

    @Unroll
    def "isTerminal supports #output"(){
        when:
        1 * terminals.isTerminal(output) >> true
        then:
        detector.isConsole(fd)

        when:
        1 * terminals.isTerminal(output) >> false
        then:
        detector.isConsole(fd) == false
        where:
        fd                  | output
        FileDescriptor.out  | Terminals.Output.Stdout
        FileDescriptor.err  | Terminals.Output.Stderr
    }

    def "isTerminal is false for any non STD out / err FileDescriptors"(){
        when:
        def isTerminal =detector.isConsole(new FileDescriptor())
        then:
        !isTerminal
        0 * terminals.isTerminal(Terminals.Output.Stdout);
        0 * terminals.isTerminal(Terminals.Output.Stderr);
    }

}
