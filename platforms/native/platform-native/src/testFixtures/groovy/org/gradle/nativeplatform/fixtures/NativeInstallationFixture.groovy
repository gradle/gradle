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

package org.gradle.nativeplatform.fixtures

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.ExecOutput
import org.gradle.test.fixtures.file.TestFile

class NativeInstallationFixture {
    private final TestFile installDir
    private final OperatingSystem os

    NativeInstallationFixture(TestFile installDir, OperatingSystem os) {
        this.installDir = installDir
        this.os = os
    }

    ExecOutput exec(Object... args) {
        assertInstalled()
        return scriptFile().exec(args)
    }

    private TestFile scriptFile() {
        if (os.windows) {
            return installDir.listFiles().find { it.file && it.name.endsWith(".bat") }
        } else {
            return installDir.listFiles().find { it.file }
        }
    }

    NativeInstallationFixture assertInstalled() {
        installDir.assertIsDir()
        final script = scriptFile()
        assert script

        def libDir = installDir.file("lib")
        libDir.assertIsDir()
        libDir.file(os.getExecutableName(script.name)).assertIsFile()
        this
    }

    NativeInstallationFixture assertNotInstalled() {
        installDir.assertDoesNotExist()
        this
    }

    NativeInstallationFixture assertIncludesLibraries(String... names) {
        def expected = names.collect { os.getSharedLibraryName(it) } as Set
        assert libraryFiles.collect { it.name } as Set == expected as Set
        this
    }

    private ArrayList<TestFile> getLibraryFiles() {
        installDir.assertIsDir()
        def libDir = installDir.file("lib")
        libDir.assertIsDir()
        def libFiles
        if (os.windows) {
            libFiles = libDir.listFiles().findAll { it.file && !it.name.endsWith(".exe") }
        } else {
            libFiles = libDir.listFiles().findAll { it.file && it.name.contains(".") }
        }
        libFiles
    }
}
