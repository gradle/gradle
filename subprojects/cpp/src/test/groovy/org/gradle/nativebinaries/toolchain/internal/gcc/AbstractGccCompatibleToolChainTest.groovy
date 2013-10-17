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
package org.gradle.nativebinaries.toolchain.internal.gcc
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.internal.ArchitectureInternal
import org.gradle.nativebinaries.internal.DefaultArchitecture
import org.gradle.nativebinaries.internal.DefaultOperatingSystem
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration
import org.gradle.nativebinaries.toolchain.internal.ToolType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

import static org.gradle.nativebinaries.internal.ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolRegistry = Mock(ToolRegistry)
    def tool = Mock(File)
    def os = Mock(OperatingSystem)
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolRegistry)
    def platform = Mock(Platform)

    def "is unavailable if not all tools can be found"() {
        when:
        def availability = toolChain.getAvailability()

        then:
        toolRegistry.locate(ToolType.CPP_COMPILER) >> null

        and:
        !availability.available
        availability.unavailableMessage == "C++ compiler cannot be found"
    }

    def "is available if all tools can be found"() {
        when:
        def availability = toolChain.getAvailability()

        then:
        toolRegistry.locate(_) >> tool
        tool.exists() >> true

        and:
        availability.available
    }

    def "supplies no additional arguments to target native binary for tool chain default"() {
        when:
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT


        then:
        toolChain.canTargetPlatform(platform)

        with(toolChain.getPlatformConfiguration(platform)) {
            linkerArgs == []
            cppCompilerArgs == []
            CCompilerArgs == []
            assemblerArgs == []
            staticLibraryArchiverArgs == []
        }
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "supplies args for supported architecture"() {
        when:
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> new DefaultArchitecture(arch, instructionSet, registerSize)

        then:
        toolChain.canTargetPlatform(platform)

        with(toolChain.getPlatformConfiguration(platform)) {
            linkerArgs == [linkerArg]
            cppCompilerArgs == [compilerArg]
            CCompilerArgs == [compilerArg]
            if (OperatingSystem.current().isMacOsX()) {
                assemblerArgs == osxAssemblerArgs
            } else {
                assemblerArgs == [assemblerArg]
            }
            staticLibraryArchiverArgs == []
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArg | osxAssemblerArgs
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | "--32"       | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | "--64"       | ["-arch", "x86_64"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "supplies args for supported architecture for i386 architecture on windows"() {
        when:
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> new DefaultArchitecture("i386", X86, 32)

        then:
        toolChain.canTargetPlatform(platform)

        with(toolChain.getPlatformConfiguration(platform)) {
            linkerArgs == ["-m32"]
            cppCompilerArgs == ["-m32"]
            CCompilerArgs == ["-m32"]
            assemblerArgs == ["--32"]
            staticLibraryArchiverArgs == []
        }
    }

    @Requires(TestPrecondition.WINDOWS)
    def "cannot target x86_64 architecture on windows"() {
        when:
        toolRegistry.locate(_) >> tool
        tool.exists() >> true

        and:
        platform.getName() >> "x64"
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> new DefaultArchitecture("x64", X86, 64)

        and:
        toolChain.target(platform)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Tool chain test cannot build for platform: x64"
    }

    def "uses supplied platform configurations in order to target binary"() {
        def platformConfig1 = Mock(TargetPlatformConfiguration)
        def platformConfig2 = Mock(TargetPlatformConfiguration)
        when:
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)

        and:
        toolChain.addPlatformConfiguration(platformConfig1)
        toolChain.addPlatformConfiguration(platformConfig2)

        and:
        platformConfig1.supportsPlatform(platform) >> false
        platformConfig2.supportsPlatform(platform) >> true

        then:
        toolChain.canTargetPlatform(platform)

        and:
        toolChain.getPlatformConfiguration(platform) == platformConfig2
    }

    static class TestToolChain extends AbstractGccCompatibleToolChain {
        TestToolChain(String name, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolRegistry tools) {
            super(name, OperatingSystem.current(), fileResolver, execActionFactory, tools)
        }

        @Override
        protected String getTypeName() {
            return "Test"
        }
    }
}
