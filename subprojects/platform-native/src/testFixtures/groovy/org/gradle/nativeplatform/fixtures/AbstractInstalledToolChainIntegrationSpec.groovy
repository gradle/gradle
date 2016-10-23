/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme
import org.gradle.test.fixtures.file.TestFile
import org.junit.runner.RunWith
/**
 * Runs a test separately for each installed tool chain.
 */
@RunWith(SingleToolChainTestRunner.class)
abstract class AbstractInstalledToolChainIntegrationSpec extends AbstractIntegrationSpec {
    static AvailableToolChains.InstalledToolChain toolChain
    File initScript

    def setup() {
        initScript = file("init.gradle") << """
allprojects { p ->
    apply plugin: ${toolChain.pluginClass}

    model {
          toolChains {
            ${toolChain.buildScriptConfig}
          }
    }
}
"""
        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    def NativeInstallationFixture installation(Object installDir, OperatingSystem os = OperatingSystem.current()) {
        return new NativeInstallationFixture(file(installDir), os)
    }

    def ExecutableFixture executable(Object path) {
        return toolChain.executable(file(path))
    }

    def TestFile objectFile(Object path) {
        return toolChain.objectFile(file(path))
    }

    def SharedLibraryFixture sharedLibrary(Object path) {
        return toolChain.sharedLibrary(file(path))
    }

    def StaticLibraryFixture staticLibrary(Object path) {
        return toolChain.staticLibrary(file(path))
    }

    def NativeBinaryFixture resourceOnlyLibrary(Object path) {
        return toolChain.resourceOnlyLibrary(file(path))
    }

    def objectFileFor(TestFile sourceFile, String rootObjectFilesDir = "build/objs/main/main${sourceType}") {
        File objectFile = new CompilerOutputFileNamingScheme()
                        .withObjectFileNameSuffix(OperatingSystem.current().isWindows() ? ".obj" : ".o")
                        .withOutputBaseFolder(file(rootObjectFilesDir))
                        .map(sourceFile)
        return file(getTestDirectory().toURI().relativize(objectFile.toURI()));
    }

    String hashFor(File inputFile){
        HashUtil.createCompactMD5(inputFile.getAbsolutePath());
    }
}
