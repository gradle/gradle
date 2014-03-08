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
import org.gradle.internal.text.TreeFormatter
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal
import org.gradle.nativebinaries.platform.internal.DefaultArchitecture
import org.gradle.nativebinaries.platform.internal.DefaultOperatingSystem
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration
import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult
import org.gradle.nativebinaries.toolchain.internal.ToolType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TreeVisitor
import spock.lang.Specification

import static ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolRegistry = Stub(ToolRegistry)
    def tool = Stub(CommandLineToolSearchResult) {
        isAvailable() >> true
    }
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolRegistry)
    def platform = Stub(Platform)

    def "is unavailable when platform is not known and is not the default platform"() {
        given:
        platform.name >> 'unknown'

        expect:
        def platformToolChain = toolChain.target(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "Don't know how to build for platform 'unknown'."
    }

    def "is unavailable when no language tools can be found and building for default platform"() {
        def missing = Stub(CommandLineToolSearchResult) {
            isAvailable() >> false
            explain(_) >> { TreeVisitor<String> visitor -> visitor.node("c compiler not found") }
        }

        given:
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        and:
        toolRegistry.locate(ToolType.C_COMPILER) >> missing
        toolRegistry.locate(ToolType.CPP_COMPILER) >> missing
        toolRegistry.locate(ToolType.OBJECTIVEC_COMPILER) >> missing
        toolRegistry.locate(ToolType.OBJECTIVECPP_COMPILER) >> missing
        toolRegistry.locate(_) >> tool

        expect:
        def platformToolChain = toolChain.target(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "c compiler not found"
    }

    def "is available when any language tool can be found and building for default platform"() {
        def missing = Stub(CommandLineToolSearchResult) {
            isAvailable() >> false
        }

        given:
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        and:
        toolRegistry.locate(ToolType.CPP_COMPILER) >> missing
        toolRegistry.locate(_) >> tool

        expect:
        toolChain.target(platform).available
    }

    def "is available when any language tool can be found and platform configuration registered for platform"() {
        def platformConfig = Mock(TargetPlatformConfiguration)

        given:
        toolRegistry.locate(_) >> tool
        platformConfig.supportsPlatform(platform) >> true

        and:
        toolChain.addPlatformConfiguration(platformConfig)

        expect:
        toolChain.target(platform).available
    }

    def "supplies no additional arguments to target native binary for tool chain default"() {
        when:
        toolRegistry.locate(_) >> tool
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        then:
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
        toolRegistry.locate(_) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture(arch, instructionSet, registerSize)

        then:
        toolChain.target(platform).available

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
        toolRegistry.locate(_) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("i386", X86, 32)

        then:
        toolChain.target(platform).available

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
        given:
        toolRegistry.locate(_) >> tool

        and:
        platform.getName() >> "x64"
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("x64", X86, 64)

        when:
        def platformToolChain = toolChain.target(platform)

        then:
        !platformToolChain.available
        getMessage(platformToolChain) == "Tool chain test cannot build for platform: x64"
    }

    def "uses supplied platform configurations in order to target binary"() {
        def platformConfig1 = Mock(TargetPlatformConfiguration)
        def platformConfig2 = Mock(TargetPlatformConfiguration)
        when:
        toolRegistry.locate(_) >> tool
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)

        and:
        toolChain.addPlatformConfiguration(platformConfig1)
        toolChain.addPlatformConfiguration(platformConfig2)

        and:
        platformConfig1.supportsPlatform(platform) >> false
        platformConfig2.supportsPlatform(platform) >> true

        then:
        toolChain.target(platform).available

        and:
        toolChain.getPlatformConfiguration(platform) == platformConfig2
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
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
