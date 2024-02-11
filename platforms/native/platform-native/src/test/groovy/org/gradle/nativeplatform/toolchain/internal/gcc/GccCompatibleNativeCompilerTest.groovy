/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeCompilerTest

abstract class GccCompatibleNativeCompilerTest extends NativeCompilerTest {
    @Override
    protected List<String> getCompilerSpecificArguments(File includeDir, File systemIncludeDir) {
        return [ '-c', '-Dfoo=bar', '-Dempty', '-firstArg', '-secondArg', '-nostdinc', '-I', includeDir.absoluteFile.toString(), '-isystem', systemIncludeDir.absoluteFile.toString() ]
    }

    def "arguments include GCC output flag and output file name"() {
        given:
        def compiler = getCompiler()
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output.ext")

        when:
        def args = compiler.getOutputArgs(Stub(NativeCompileSpec), outputFile)

        then:
        args == [ '-o', outputFile.absoluteFile.toString() ]
    }

}
