/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.internal

import spock.lang.Specification

class ArchitectureNotationParserTest extends Specification {
    def parser = ArchitectureNotationParser.parser()

    def "parses intel architectures"() {
        when:
        def arch = parser.parseNotation(archString)

        then:
        arch.instructionSet == ArchitectureInternal.InstructionSet.X86
        arch.registerSize == registerSize
        arch.name == archString

        where:
        archString | registerSize
        "x86"      | 32
        "X86"      | 32
        "i386"     | 32
        "ia-32"    | 32
        "IA-32"    | 32
        "x86_64"   | 64
        "x86-64"   | 64
        "amd64"    | 64
        "AMD64"    | 64
        "x64"      | 64
    }

    def "parses itanium architecture"() {
        when:
        def arch = parser.parseNotation("ia-64")
        then:
        arch.instructionSet == ArchitectureInternal.InstructionSet.ITANIUM
        arch.registerSize == 64
        arch.name == "ia-64"
    }

    def "parses ppc architecture"() {
        when:
        def arch = parser.parseNotation(archString)

        then:
        arch.instructionSet == ArchitectureInternal.InstructionSet.PPC
        arch.registerSize == registerSize
        arch.name == archString

        where:
        archString | registerSize
        "ppc"      | 32
        "PPC"      | 32
        "ppc64"    | 64
        "PPC64"    | 64
    }

    def "parses sparc architectures"() {
        when:
        def arch = parser.parseNotation(archString)

        then:
        arch.instructionSet == ArchitectureInternal.InstructionSet.SPARC
        arch.registerSize == registerSize
        arch.name == archString

        where:
        archString   | registerSize
        "sparc"      | 32
        "SPARC"      | 32
        "sparc32"    | 32
        "sparc-v7"   | 32
        "sparc-v8"   | 32
        "sparc64"    | 64
        "ultrasparc" | 64
        "sparc-v9"   | 64
    }

    def "parses arm architecture"() {
        when:
        def arch = parser.parseNotation("arm")
        then:
        arch.instructionSet == ArchitectureInternal.InstructionSet.ARM
        arch.registerSize == 32
        arch.name == "arm"
    }
}
