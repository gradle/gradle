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

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.text.TreeFormatter
import org.gradle.nativebinaries.toolchain.ConfigurableToolChain
import org.gradle.nativebinaries.toolchain.GccTool
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain
import org.gradle.nativebinaries.toolchain.internal.tools.DefaultTool
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal
import org.gradle.nativebinaries.platform.internal.DefaultArchitecture
import org.gradle.nativebinaries.platform.internal.DefaultOperatingSystem

import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult
import org.gradle.nativebinaries.toolchain.internal.ToolType

import org.gradle.nativebinaries.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TreeVisitor
import spock.lang.Specification

import static ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolSearchPath = Stub(ToolSearchPath)
    def tool = Stub(CommandLineToolSearchResult) {
        isAvailable() >> true
    }

    def instantiator = Mock(Instantiator)
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolSearchPath, instantiator)
    def platform = Stub(Platform)

    def setup() {
        instantiator.newInstance(DefaultConfigurableToolChain.class, _) >> { args ->
            new DefaultConfigurableToolChain(args[1][0], args[1][1], args[1][2], args[1][3], args[1][4])
        }
    }

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
        toolChain.target(platform, Mock(Action))

        expect:
        toolChain.select(platform).available
    }

    def "selected toolChain applies platform configuration action"() {
        Platform platform1 = Mock(Platform)
        Platform platform2 = Mock(Platform)
        platform1.getName() >> "platform1"
        platform2.getName() >> "platform2"
        when:
        toolSearchPath.locate(_, _) >> tool

        int platformActionApplied = 0
        toolChain.target([platform1.getName(), platform2.getName()], new Action<ConfigurableToolChain>() {
            void execute(ConfigurableToolChain configurableToolChain) {
                platformActionApplied++;
            }
        });
        PlatformToolChain selected = toolChain.select(platform1)
        then:
        selected.isAvailable();
        assert platformActionApplied == 1
        when:

        selected = toolChain.select(platform2)
        then:
        selected.isAvailable()
        assert platformActionApplied == 2
    }

    def "supplies no additional arguments to target native binary for tool chain default"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        ConfigurableToolChain configurableToolChain = newConfigurableToolChain()
        then:

        with(toolChain.getPlatformConfiguration(platform).apply(configurableToolChain)) {
            def args = []
            configurableToolChain.getByName("linker").getArgAction().execute(args)
            args == []
            configurableToolChain.getByName("cppCompiler").getArgAction().execute(args)
            args == []
            configurableToolChain.getByName("cCompiler").getArgAction().execute(args)
            args == []
            configurableToolChain.getByName("assembler").getArgAction().execute(args)
            args == []
            configurableToolChain.getByName("staticLibArchiver").getArgAction().execute(args)
            args == []
            configurableToolChain.getByName("objcCompiler").getArgAction().execute(args)
            args == []
        }
    }


    @Requires(TestPrecondition.NOT_WINDOWS)
    def "supplies args for supported architecture"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture(arch, instructionSet, registerSize)

        then:
        toolChain.select(platform).available

        with(toolChain.getPlatformConfiguration(platform).apply(newConfigurableToolChain())) {
            argsFor(getByName("linker")) == [linkerArg]

            argsFor(getByName("cppCompiler")) == [compilerArg]
            argsFor(getByName("cCompiler")) == [compilerArg]

            if (OperatingSystem.current().isMacOsX()) {
                argsFor(getByName("assembler")) == osxAssemblerArgs
            } else {
                argsFor(getByName("assembler")) == [assemblerArg]
            }
            argsFor(getByName("staticLibArchiver")) == []
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArg | osxAssemblerArgs
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | "--32"       | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | "--64"       | ["-arch", "x86_64"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "supplies args for supported architecture for i386 architecture on windows"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("i386", X86, 32)

        then:
        toolChain.select(platform).available

        with(toolChain.getPlatformConfiguration(platform).apply(newConfigurableToolChain())) {
            argsFor(getByName("cppCompiler")) == ["-m32"]
            argsFor(getByName("cCompiler")) == ["-m32"]
            argsFor(getByName("linker")) == ["-m32"]
            argsFor(getByName("assembler")) == ["--32"]
            argsFor(getByName("staticLibArchiver")) == []
        }
    }

    @Requires(TestPrecondition.WINDOWS)
    def "cannot target x86_64 architecture on windows"() {
        given:
        toolSearchPath.locate(_, _) >> tool

        and:
        platform.getName() >> "x64"
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("x64", X86, 64)

        when:
        def platformToolChain = toolChain.select(platform)

        then:
        !platformToolChain.available
        getMessage(platformToolChain) == "Don't know how to build for platform 'x64'."
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

        PlatformToolChain platformToolChain = toolChain.select(platform)

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
        toolChain.target(platform, new Action<ConfigurableToolChain>() {
            void execute(ConfigurableToolChain configurableToolChain) {
                configurationApplied = true;
            }
        })

        then:
        toolChain.select(platform).available
        configurationApplied
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
    }

    static class TestToolChain extends AbstractGccCompatibleToolChain {
        TestToolChain(String name, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolSearchPath tools, Instantiator instantiator) {
            super(name, OperatingSystem.current(), fileResolver, execActionFactory, tools, instantiator)
            add(new DefaultTool("cppCompiler", ToolType.CPP_COMPILER, "g++"));
            add(new DefaultTool("objcCompiler", ToolType.OBJECTIVEC_COMPILER, "gcc"));
            add(new DefaultTool("objcppCompiler", ToolType.OBJECTIVECPP_COMPILER, "g++"));
            add(new DefaultTool("assembler", ToolType.ASSEMBLER, "as"));
            add(new DefaultTool("linker", ToolType.LINKER, "ld"));
            add(new DefaultTool("staticLibArchiver", ToolType.STATIC_LIB_ARCHIVER, "ar"));
        }

        @Override
        protected String getTypeName() {
            return "Test"
        }
    }


    ConfigurableToolChain newConfigurableToolChain() {
        def tools = [:]
        tools.put("assembler", new DefaultTool("assembler", ToolType.ASSEMBLER, ""))
        tools.put("cCompiler", new DefaultTool("cCompiler", ToolType.C_COMPILER, ""))
        tools.put("cppCompiler", new DefaultTool("cppCompiler", ToolType.CPP_COMPILER, ""))
        tools.put("objcCompiler", new DefaultTool("objcCompiler", ToolType.OBJECTIVEC_COMPILER, ""))
        tools.put("objcppCompiler", new DefaultTool("objcppCompiler", ToolType.OBJECTIVECPP_COMPILER, ""))
        tools.put("linker", new DefaultTool("linker", ToolType.LINKER, ""))
        tools.put("staticLibArchiver", new DefaultTool("staticLibArchiver", ToolType.STATIC_LIB_ARCHIVER, ""))

        ConfigurableToolChain configurableToolChain = new DefaultConfigurableToolChain(GccTool.class,
                tools,
                instantiator,
                "PlatformTestToolChain",
                "Platform specific toolchain")

        return configurableToolChain;
    }

    def argsFor(def tool) {
        def args = []
        tool.getArgAction().execute(args)
        args
    }
}
