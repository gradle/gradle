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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.NativeCompilerTest

abstract class VisualCppNativeCompilerTest extends NativeCompilerTest {
    @Override
    protected List<String> getCompilerSpecificArguments(File includeDir) {
        ['/nologo', '/Dfoo=bar', '/Dempty', '-firstArg', '-secondArg', '/c',
         '/I' + includeDir.absoluteFile.toString()]
    }

    def "arguments include MSVC output flag and output file name"() {
        given:
        def invocation = Mock(MutableCommandLineToolInvocation)
        def compiler = getCompiler(invocation)
        def testDir = tmpDirProvider.testDirectory
        def args = []
        def outputFile = testDir.file("output.ext")

        when:
        compiler.addOutputArgs(args, outputFile)

        then:
        args == ['/Fo' + outputFile.absoluteFile.toString()]
    }

}
