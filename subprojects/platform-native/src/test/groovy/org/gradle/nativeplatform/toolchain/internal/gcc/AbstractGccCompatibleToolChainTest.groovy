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
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.text.TreeFormatter
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.*
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionResult
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.TreeVisitor
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import static org.gradle.nativeplatform.platform.internal.ArchitectureInternal.InstructionSet.X86

@UsesNativeServices
class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolSearchPath = Stub(ToolSearchPath)
    def tool = Stub(CommandLineToolSearchResult) {
        isAvailable() >> true
    }
    def missing = Stub(CommandLineToolSearchResult) {
        isAvailable() >> false
    }
    def correctCompiler = Stub(GccVersionResult) {
        isAvailable() >> true
    }
    def metaDataProvider = Stub(CompilerMetaDataProvider)
    def operatingSystem = Stub(OperatingSystem)
    def buildOperationExecutor = Stub(BuildOperationExecutor)
    def compilerOutputFileNamingSchemeFactory = Stub(CompilerOutputFileNamingSchemeFactory)
    def workerLeaseService = Stub(WorkerLeaseService)

    def instantiator = DirectInstantiator.INSTANCE
    def toolChain = new TestNativeToolChain("test", buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, toolSearchPath, metaDataProvider, instantiator, workerLeaseService)
    def platform = Stub(NativePlatformInternal)

    def dummyOs = new DefaultOperatingSystem("currentOS", OperatingSystem.current())
    def dummyArch = Architectures.forInput("x86_64")

    def "is unavailable when platform is not known and is not the default platform"() {
        given:
        platform.name >> 'unknown'

        expect:
        def platformToolChain = toolChain.select(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "Don't know how to build for platform 'unknown'."
    }

    def "is unavailable when no language tools can be found"() {
        def compilerMissing = Stub(CommandLineToolSearchResult) {
            isAvailable() >> false
            explain(_) >> { TreeVisitor<String> visitor -> visitor.node("c compiler not found") }
        }

        given:
        platform.operatingSystem >> dummyOs
        platform.architecture >> dummyArch

        and:
        toolSearchPath.locate(ToolType.C_COMPILER, "gcc") >> compilerMissing
        toolSearchPath.locate(ToolType.CPP_COMPILER, "g++") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVEC_COMPILER, "gcc") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVECPP_COMPILER, "g++") >> missing

        expect:
        def platformToolChain = toolChain.select(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "c compiler not found"
    }

    def "is unavailable when a compiler is found with incorrect implementation"() {
        def wrongCompiler = Stub(GccVersionResult) {
            isAvailable() >> false
            explain(_) >> { TreeVisitor<String> visitor -> visitor.node("c compiler is not gcc") }
        }

        given:
        platform.operatingSystem >> dummyOs
        platform.architecture >> dummyArch

        and:
        toolSearchPath.locate(_, _) >> tool
        metaDataProvider.getGccMetaData(_, _) >> wrongCompiler

        expect:
        def platformToolChain = toolChain.select(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "c compiler is not gcc"
    }

    def "is available when any language tool can be found and compiler has correct implementation"() {
        given:
        platform.operatingSystem >> dummyOs
        platform.architecture >> dummyArch

        and:
        toolSearchPath.locate(ToolType.C_COMPILER, "gcc") >> missing
        toolSearchPath.locate(_, _) >> tool
        metaDataProvider.getGccMetaData(_, _) >> correctCompiler

        and:

        expect:
        toolChain.select(platform).available
    }

    def "is available when platform configuration registered for platform and tools are available"() {
        given:
        platform.name >> "SomePlatform"
        toolChain.target("SomePlatform", Mock(Action))

        and:
        toolSearchPath.locate(_, _) >> tool
        metaDataProvider.getGccMetaData(_, _) >> correctCompiler

        expect:
        toolChain.select(platform).available
    }

    def "selected toolChain applies platform configuration action"() {
        def platform1 = Mock(NativePlatformInternal)
        def platform2 = Mock(NativePlatformInternal)
        platform1.name >> "platform1"
        platform2.name >> "platform2"

        platform1.operatingSystem >> dummyOs
        platform2.operatingSystem >> dummyOs

        toolSearchPath.locate(_, _) >> tool
        metaDataProvider.getGccMetaData(_, _) >> correctCompiler

        given:
        int platformActionApplied = 0
        toolChain.target([platform1.getName(), platform2.getName()], new Action<NativePlatformToolChain>() {
            void execute(NativePlatformToolChain configurableToolChain) {
                platformActionApplied++;
            }
        });

        when:
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

    def "supplies no additional arguments to target native binary for tool chain default"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> dummyOs
        platform.getArchitecture() >> dummyArch
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

    def "supplies args for supported architecture for non-os x platforms"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> dummyOs
        platform.architecture >> Architectures.forInput(arch)
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
            argsFor(platformToolChain.assembler) == [compilerArg]
            argsFor(platformToolChain.staticLibArchiver) == []
        }

        where:
        arch     | linkerArg | compilerArg
        "i386"   | "-m32"    | "-m32"
        "x86_64" | "-m64"    | "-m64"
    }

    def "supplies args for supported architecture for os x platforms"() {
        def action = Mock(Action)

        given:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> new DefaultOperatingSystem("osx", OperatingSystem.MAC_OS)
        platform.architecture >> new DefaultArchitecture(arch)

        toolChain.target(platform.name)
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
            argsFor(platformToolChain.assembler) == assemblerArgs
            argsFor(platformToolChain.staticLibArchiver) == []
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArgs
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | ["-arch", "x86_64"]
    }

    def "uses supplied platform configurations in order to target binary"() {
        setup:
        _ * platform.getName() >> "platform2"
        def platformConfig1 = Mock(Action)
        def platformConfig2 = Mock(Action)

        toolSearchPath.locate(_, _) >> tool
        metaDataProvider.getGccMetaData(_, _) >> correctCompiler

        toolChain.target("platform1", platformConfig1)
        toolChain.target("platform2", platformConfig2)

        when:
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
        metaDataProvider.getGccMetaData(_, _) >> correctCompiler

        and:
        toolChain.target(platform.getName(), new Action<NativePlatformToolChain>() {
            void execute(NativePlatformToolChain configurableToolChain) {
                configurationApplied = true;
            }
        })

        then:
        toolChain.select(platform).available
        configurationApplied
    }

    def "provided action can configure platform tool chain"() {
        given:
        platform.operatingSystem >> dummyOs
        platform.architecture >> dummyArch

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

    static class TestNativeToolChain extends AbstractGccCompatibleToolChain {
        TestNativeToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, ToolSearchPath tools, CompilerMetaDataProvider metaDataProvider, Instantiator instantiator, WorkerLeaseService workerLeaseService) {
            super(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, tools, metaDataProvider, instantiator, workerLeaseService)
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
