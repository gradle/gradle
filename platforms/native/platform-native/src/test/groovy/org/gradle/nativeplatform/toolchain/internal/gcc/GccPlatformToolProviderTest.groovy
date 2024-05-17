/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.ComponentFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

class GccPlatformToolProviderTest extends Specification {

    def buildOperationExecuter = Mock(BuildOperationExecutor)
    def operatingSystem = Mock(OperatingSystemInternal)
    def toolSearchPath = Mock(ToolSearchPath)
    def toolRegistry = Mock(ToolRegistry)
    def execActionFactory = Mock(ExecActionFactory)
    def namingSchemeFactory = Mock(CompilerOutputFileNamingSchemeFactory)
    def workerLeaseService = Mock(WorkerLeaseService)
    def metaDataProvider = Mock(CompilerMetaDataProvider)
    def targetPlatform = Mock(NativePlatformInternal)
    def platformToolProvider = new GccPlatformToolProvider(buildOperationExecuter, operatingSystem, toolSearchPath, toolRegistry, execActionFactory, namingSchemeFactory, true, workerLeaseService, metaDataProvider)

    def "arguments #args are passed to metadata provider for #toolType.toolName"() {
        def metaData = Stub(GccMetadata)
        def libs = Stub(SystemLibraries)

        when:
        def result = platformToolProvider.getSystemLibraries(toolType)

        then:
        result == libs
        1 * metaDataProvider.getCompilerMetaData(_, _) >> {
            arguments[1].execute(assertingCompilerExecSpecArguments(args))
            new ComponentFound(metaData)
        }
        1 * toolRegistry.getTool(toolType) >> new DefaultGccCommandLineToolConfiguration(toolType, 'exe')
        1 * toolSearchPath.locate(toolType, 'exe') >> Mock(CommandLineToolSearchResult)
        _ * metaData.systemLibraries >> libs

        where:
        toolType                       | args
        ToolType.CPP_COMPILER          | ['-x', 'c++']
        ToolType.C_COMPILER            | ['-x', 'c']
        ToolType.OBJECTIVEC_COMPILER   | ['-x', 'objective-c']
        ToolType.OBJECTIVECPP_COMPILER | ['-x', 'objective-c++']
        ToolType.ASSEMBLER             | []
    }

    def "gets compiler metadata from the provider for #toolType.toolName"() {
        when:
        platformToolProvider.getCompilerMetadata(toolType)

        then:
        1 * metaDataProvider.getCompilerMetaData(_, _) >> {
            arguments[1].execute(assertingCompilerExecSpecArguments(args))
            Mock(SearchResult)
        }
        1 * toolRegistry.getTool(toolType) >> new DefaultGccCommandLineToolConfiguration(toolType, 'exe')
        1 * toolSearchPath.locate(toolType, 'exe') >> Mock(CommandLineToolSearchResult)

        where:
        toolType                       | args
        ToolType.CPP_COMPILER          | ['-x', 'c++']
        ToolType.C_COMPILER            | ['-x', 'c']
        ToolType.OBJECTIVEC_COMPILER   | ['-x', 'objective-c']
        ToolType.OBJECTIVECPP_COMPILER | ['-x', 'objective-c++']
        ToolType.ASSEMBLER             | []
    }

    CompilerMetaDataProvider.CompilerExecSpec assertingCompilerExecSpecArguments(Iterable<String> expectedArgs) {
        return new CompilerMetaDataProvider.CompilerExecSpec() {
            @Override
            CompilerMetaDataProvider.CompilerExecSpec environment(String key, String value) {
                return this
            }

            @Override
            CompilerMetaDataProvider.CompilerExecSpec executable(File executable) {
                return this
            }

            @Override
            CompilerMetaDataProvider.CompilerExecSpec args(Iterable<String> args) {
                assert args == expectedArgs
                return this
            }
        }
    }
}
