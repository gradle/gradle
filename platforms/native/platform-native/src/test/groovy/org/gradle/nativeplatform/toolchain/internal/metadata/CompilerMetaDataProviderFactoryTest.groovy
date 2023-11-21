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

package org.gradle.nativeplatform.toolchain.internal.metadata

import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

class CompilerMetaDataProviderFactoryTest extends Specification {

    def execActionFactory = Mock(ExecActionFactory)
    def execAction = Mock(ExecAction)
    def execResult = Mock(ExecResult)
    def factory = new CompilerMetaDataProviderFactory(execActionFactory)

    def "caches result of actual #compiler metadata provider"() {
        def binary = new File("any")
        when:
        def metadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(binary) }

        then:
        interaction compilerShouldBeExecuted

        when:
        def newMetadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(binary) }

        then:
        0 * _
        newMetadata.is(metadata)

        where:
        compiler << ['gcc', 'clang', 'swiftc']
    }

    def "different #compiler executables are probed and cached"() {
        def firstBinary = new File("first")
        def secondBinary = new File("second")
        when:
        def firstMetadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(firstBinary) }

        then:
        interaction compilerShouldBeExecuted

        when:
        def secondMetadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(secondBinary) }

        then:
        interaction compilerShouldBeExecuted
        firstMetadata != secondMetadata

        when:
        def firstMetadataAgain = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(firstBinary) }

        then:
        0 * _
        firstMetadataAgain.is firstMetadata

        where:
        compiler << ['gcc', 'clang', 'swiftc']
    }

    def "different #compiler arguments are probed and cached"() {
        def binary = new File("any")
        def firstArgs = ["-m32"]
        def secondArgs = ["-m64"]
        when:
        def firstMetadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(binary).args(firstArgs) }

        then:
        interaction compilerShouldBeExecuted

        when:
        def secondMetadata = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(binary).args(secondArgs) }

        then:
        interaction compilerShouldBeExecuted
        firstMetadata != secondMetadata

        when:
        def firstMetadataAgain = metadataProvider(compiler).getCompilerMetaData([]) { it.executable(binary).args(firstArgs) }

        then:
        0 * _
        firstMetadataAgain.is firstMetadata

        where:
        compiler << ['gcc', 'clang', 'swiftc']
    }

    def "different #compiler paths are probed and cached"() {
        def binary = new File("any")
        def firstPath = []
        def secondPath = [new File("/usr/local/bin")]
        when:
        def firstMetadata = metadataProvider(compiler).getCompilerMetaData(firstPath) { it.executable(binary) }

        then:
        interaction compilerShouldBeExecuted

        when:
        def secondMetadata = metadataProvider(compiler).getCompilerMetaData(secondPath) { it.executable(binary) }

        then:
        interaction compilerShouldBeExecuted
        firstMetadata != secondMetadata

        when:
        def firstMetadataAgain = metadataProvider(compiler).getCompilerMetaData(firstPath) { it.executable(binary) }

        then:
        0 * _
        firstMetadataAgain.is firstMetadata

        where:
        compiler << ['gcc', 'clang', 'swiftc']
    }

    private <T extends CompilerMetadata> CompilerMetaDataProvider<T> metadataProvider(String compiler) {
        factory."${compiler}"()
    }

    Closure compilerShouldBeExecuted = {
        1 * execActionFactory.newExecAction() >> execAction
        1 * execAction.execute() >> execResult
        1 * execResult.exitValue >> 1
    }

}
