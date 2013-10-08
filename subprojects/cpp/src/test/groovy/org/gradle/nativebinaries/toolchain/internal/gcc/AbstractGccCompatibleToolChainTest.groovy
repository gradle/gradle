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
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.Tool
import org.gradle.nativebinaries.internal.ArchitectureInternal
import org.gradle.nativebinaries.internal.DefaultArchitecture
import org.gradle.nativebinaries.internal.DefaultOperatingSystem
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.toolchain.ToolChainPlatformConfiguration
import org.gradle.nativebinaries.toolchain.internal.ToolType
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

import static org.gradle.nativebinaries.internal.ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolRegistry = Mock(ToolRegistry)
    def tool = Mock(File)
    def os = Mock(OperatingSystem)
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolRegistry)

    def cppCompiler = Mock(Tool)
    def assembler = Mock(Tool)
    def linker = Mock(Tool)
    def extensions = Mock(ExtensionContainer) {
    }
    def nativeBinary = Mock(ExtendedNativeBinary) {
        getExtensions() >> extensions
        getLinker() >> linker
    }
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

    def "does not target native binary for tool chain default"() {
        when:
        nativeBinary.getTargetPlatform() >> platform
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        and:
        toolChain.targetNativeBinaryForPlatform(nativeBinary)

        then:
        0 * nativeBinary._
    }

    def "targets native binary for architecture"() {
        when:
        nativeBinary.getTargetPlatform() >> platform
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> new DefaultArchitecture(arch, instructionSet, registerSize)

        and:
        toolChain.targetNativeBinaryForPlatform(nativeBinary)

        then:
        1 * nativeBinary.getLinker() >> linker
        1 * linker.args(linkerArg)
        1 * extensions.findByName("cppCompiler") >> cppCompiler
        1 * cppCompiler.args(compilerArg)
        1 * extensions.findByName("cCompiler") >> null
        1 * extensions.findByName("assembler") >> assembler
        if (OperatingSystem.current().isMacOsX()) {
            1 * assembler.args(osxAssemblerArg as String[])
        } else {
            1 * assembler.args(assemblerArg)
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArg | osxAssemblerArg
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | "--32"       | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | "--64"       | ["-arch", "x86_64"]
    }

    def "uses supplied platform configurations in order to target binary"() {
        def platformConfig1 = Mock(ToolChainPlatformConfiguration)
        def platformConfig2 = Mock(ToolChainPlatformConfiguration)
        when:
        nativeBinary.getTargetPlatform() >> platform
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)

        and:
        toolChain.addPlatformConfiguration(platformConfig1)
        toolChain.addPlatformConfiguration(platformConfig2)

        and:
        toolChain.targetNativeBinaryForPlatform(nativeBinary)

        then:
        1 * platformConfig1.supportsPlatform(platform) >> false
        1 * platformConfig2.supportsPlatform(platform) >> true
        1 * platformConfig2.configureBinaryForPlatform(nativeBinary)
        0 * nativeBinary._
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

    interface ExtendedNativeBinary extends NativeBinaryInternal, ExtensionAware {}
}
