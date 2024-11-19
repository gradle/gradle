/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.gradle.process.internal.ExecException
import spock.lang.Specification

class DefaultOsXJavaHomeCommandTest extends Specification {

    def "parses new format output"() {
        given:
        def output = """
Matching Java Virtual Machines (11):
    9, x86_64:\t"Java SE 9-ea"\t/Library/Java/JavaVirtualMachines/jdk-9.jdk/Contents/Home
    1.8.0, x86_64:\t"Java SE 8"\t/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home
    1.7.0_17, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home
    1.7.0_07, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_07.jdk/Contents/Home
    1.7.0_06, x86_64:\t"Java SE 7"\t/Library/Java/JavaVirtualMachines/jdk1.7.0_06.jdk/Contents/Home
    1.7.0-ea-b223-ea-b223-ea-b223, x86_64:\t"Java SE 7 Developer Preview"\t/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home
    1.7.0-ea-b223-ea-b223-ea-b223, i386:\t"Java SE 7 Developer Preview"\t/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home
    1.7.0, x86_64:\t"OpenJDK 7"\t/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home
    1.7.0, i386:\t"OpenJDK 7"\t/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home
    1.6.0_65-b14-462, x86_64:\t"Java SE 6"\t/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    1.6.0_65-b14-462, i386:\t"Java SE 6"\t/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
"""

        when:
        def result = DefaultOsXJavaHomeCommand.parse(new StringReader(output))

        then:
        result.containsAll([
            new File("/Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home"),
            new File("/Library/Java/JavaVirtualMachines/JDK 1.7.0 Developer Preview.jdk/Contents/Home"),
            new File("/Library/Java/JavaVirtualMachines/jdk-9.jdk/Contents/Home"),
            new File("/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home"),
            new File("/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home"),
            new File("/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home"),
        ])
        result.size() == 8
    }

    def "parses old format output"() {
        given:
        def output = """
Matching Java Virtual Machines (2):
    1.6.0_17 (x86_64):\t/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home
    1.6.0_17 (x86_64):\t/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home
"""
        when:
        def result = DefaultOsXJavaHomeCommand.parse(new StringReader(output))

        then:
        result.containsAll([
            new File("/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home"),
        ])
        result.size() == 1
    }

    def "can parse output with no installations"() {
        given:
        def output = """
Unable to find any JVMs matching version "(null)".
Matching Java Virtual Machines (0):

Default Java Virtual Machines (0):

No Java runtime present, try --request to install.
"""
        when:
        def result = DefaultOsXJavaHomeCommand.parse(new StringReader(output))

        then:
        result.isEmpty()
    }

    def "returns empty set when command fails"() {
        def parser = new DefaultOsXJavaHomeCommand(Mock(ClientExecHandleBuilderFactory)) {
            @Override
            protected void executeCommand(ByteArrayOutputStream outputStream) {
                throw new ExecException("command failed")
            }
        }
        expect:
        parser.findJavaHomes().isEmpty()
    }
}
