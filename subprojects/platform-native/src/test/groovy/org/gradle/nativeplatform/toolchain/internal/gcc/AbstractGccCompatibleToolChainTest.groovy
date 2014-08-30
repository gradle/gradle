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
package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.text.TreeFormatter
import org.gradle.nativeplatform.platform.internal.*
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.PlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TreeVisitor
import spock.lang.Specification

import static org.gradle.nativeplatform.platform.internal.ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolSearchPath = Stub(ToolSearchPath)
    def tool = Stub(CommandLineToolSearchResult) {
        isAvailable() >> true
    }

    def instantiator = new DirectInstantiator()
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolSearchPath, instantiator)
    def platform = Stub(PlatformInternal)

    def "is unavailable when platform is not known and is not the default platform"() {
        given:
        platform.name >> 'unknown'

        expect:
        def platformToolChain = toolChain.select(platform)
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
        toolSearchPath.locate(ToolType.C_COMPILER, "gcc") >> missing
        toolSearchPath.locate(ToolType.CPP_COMPILER, "g++") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVEC_COMPILER, "gcc") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVECPP_COMPILER, "g++") >> missing

        expect:
        def platformToolChain = toolChain.select(platform)
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
        toolSearchPath.locate(ToolType.CPP_COMPILER, "g++") >> missing
        toolSearchPath.locate(_, _) >> tool

        expect:
        toolChain.select(platform).available
    }

    def "is available when any language tool can be found and platform configuration registered for platform"() {
        given:
        toolSearchPath.locate(_, _) >> tool
        platform.name >> "SomePlatform"
        toolChain.target("SomePlatform", Mock(Action))

        expect:
        toolChain.select(platform).available
    }

    def "selected toolChain applies platform configuration action"() {
        def platform1 = Mock(PlatformInternal)
        def platform2 = Mock(PlatformInternal)
        platform1.getName() >> "platform1"

        platform1.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform2.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform2.getName() >> "platform2"
        when:
        toolSearchPath.locate(_, _) >> tool

        int platformActionApplied = 0
        toolChain.target([platform1.getName(), platform2.getName()], new Action<PlatformToolChain>() {
            void execute(PlatformToolChain configurableToolChain) {
                platformActionApplied++;
            }
        });
        PlatformToolProvider selected = toolChain.select(platform1)
        then:
        selected.isAvailable();
        assert platformActionApplied == 1
        when:

        selected = toolChain.select(platform2)
        then:
        selected.isAvailable()
        assert platformActionApplied == 2
    }


    def "selected toolChain uses objectfile suffix based on targetplatform"() {
        def platform1 = Mock(PlatformInternal)
        def platform2 = Mock(PlatformInternal)
        platform1.getName() >> "platform1"
        def  platformOSWin = Mock(OperatingSystemInternal)
        platformOSWin.isWindows() >> true
        def  platformOSNonWin = Mock(OperatingSystemInternal)
        platformOSNonWin.isWindows() >> false
        platform1.getOperatingSystem() >> platformOSWin
        platform2.getOperatingSystem() >> platformOSNonWin
        platform2.getName() >> "platform2"
        when:
        toolSearchPath.locate(_, _) >> tool

        toolChain.target(platform1.getName())
        toolChain.target(platform2.getName())
        PlatformToolProvider selected = toolChain.select(platform1)
        then:
        selected.outputFileSuffix == ".obj"
        when:

        selected = toolChain.select(platform2)
        then:
        selected.outputFileSuffix == ".o"
    }


    def "supplies no additional arguments to target native binary for tool chain default"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            argsFor(platformToolChain.linker) == []
            argsFor(platformToolChain.cCompiler) == []
            argsFor(platformToolChain.cppCompiler) == []
            argsFor(platformToolChain.assembler) == []
            argsFor(platformToolChain.staticLibArchiver) == []
            argsFor(platformToolChain.objcCompiler) == []
            argsFor(platformToolChain.objcppCompiler) == []
        }
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "supplies args for supported architecture"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture(arch, instructionSet, registerSize)
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            argsFor(platformToolChain.linker) == [linkerArg]

            argsFor(platformToolChain.cppCompiler) == [compilerArg]
            argsFor(platformToolChain.cCompiler) == [compilerArg]
            argsFor(platformToolChain.objcCompiler) == [compilerArg]
            argsFor(platformToolChain.objcppCompiler) == [compilerArg]

            if (OperatingSystem.current().isMacOsX()) {
                argsFor(platformToolChain.assembler) == osxAssemblerArgs
            } else {
                argsFor(platformToolChain.assembler) == [assemblerArg]
            }
            argsFor(platformToolChain.staticLibArchiver) == []
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArg | osxAssemblerArgs
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | "--32"       | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | "--64"       | ["-arch", "x86_64"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "supplies args for supported architecture for i386 architecture on windows"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("i386", X86, 32)
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            argsFor(platformToolChain.cppCompiler) == ["-m32"]
            argsFor(platformToolChain.cCompiler) == ["-m32"]
            argsFor(platformToolChain.objcCompiler) == ["-m32"]
            argsFor(platformToolChain.objcppCompiler) == ["-m32"]
            argsFor(platformToolChain.linker) == ["-m32"]
            argsFor(platformToolChain.assembler) == ["--32"]
            argsFor(platformToolChain.staticLibArchiver) == []
        }
    }

    def "uses supplied platform configurations in order to target binary"() {
        setup:
        _ * platform.getName() >> "platform2"
        def platformConfig1 = Mock(Action)
        def platformConfig2 = Mock(Action)

        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)
        toolChain.target("platform1", platformConfig1)
        toolChain.target("platform2", platformConfig2)

        PlatformToolProvider platformToolChain = toolChain.select(platform)

        then:
        platformToolChain.available

        and:
        1 * platformConfig2.execute(_)
    }

    def "uses platform specific toolchain configuration"() {
        given:
        boolean configurationApplied = false
        _ * platform.getName() >> "testPlatform"
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)

        and:
        toolChain.target(platform.getName(), new Action<PlatformToolChain>() {
            void execute(PlatformToolChain configurableToolChain) {
                configurationApplied = true;
            }
        })

        then:
        toolChain.select(platform).available
        configurationApplied
    }

    def "provided action can configure platform tool chain"() {
        given:
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        def action = Mock(Action)
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            assert platformToolChain.platform == platform
            assert platformToolChain.cCompiler
            assert platformToolChain.cppCompiler
            assert platformToolChain.objcCompiler
            assert platformToolChain.objcppCompiler
            assert platformToolChain.linker
            assert platformToolChain.staticLibArchiver
        }
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
    }

    static class TestToolChain extends AbstractGccCompatibleToolChain {
        TestToolChain(String name, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolSearchPath tools, Instantiator instantiator) {
            super(name, org.gradle.internal.os.OperatingSystem.current(), fileResolver, execActionFactory, tools, instantiator)
        }

        @Override
        protected String getTypeName() {
            return "Test"
        }
    }

    def argsFor(GccCommandLineToolConfigurationInternal tool) {
        def args = []
        tool.getArgAction().execute(args)
        args
    }
}
