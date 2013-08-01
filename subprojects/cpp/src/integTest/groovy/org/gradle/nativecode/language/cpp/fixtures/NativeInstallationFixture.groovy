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

package org.gradle.nativecode.language.cpp.fixtures

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile

class NativeInstallationFixture {
    private final TestFile installDir

    NativeInstallationFixture(TestFile installDir) {
        this.installDir = installDir
    }

    Map<String, ?> exec(Object... args) {
        assertInstalled()
        if (OperatingSystem.current().windows) {
            def exe = installDir.listFiles().find { it.file && it.name.endsWith(".exe") }
            return exe.exec(args)
        } else {
            def script = installDir.listFiles().find { it.file }
            return script.exec(args)
        }
    }

    NativeInstallationFixture assertInstalled() {
        installDir.assertIsDir()
        if (OperatingSystem.current().windows) {
            def exe = installDir.listFiles().find { it.file && it.name.endsWith(".exe") }
            assert exe
        } else {
            def libDir = installDir.file("lib")
            libDir.assertIsDir()
            def script = installDir.listFiles().find { it.file }
            assert script
            libDir.file(script.name).assertIsFile()
        }
        this
    }

    NativeInstallationFixture assertIncludesLibraries(String... names) {
        installDir.assertIsDir()
        def expected = names.collect { OperatingSystem.current().getSharedLibraryName(it) } as Set
        if (OperatingSystem.current().windows) {
            def libs = installDir.listFiles().findAll { it.file && !it.name.endsWith(".exe") }.collect { it.name }
            assert libs as Set == expected as Set
        } else {
            def libDir = installDir.file("lib")
            libDir.assertIsDir()
            def libs = libDir.listFiles().findAll { it.file && it.name.contains(".") }.collect { it.name }
            assert libs as Set == expected as Set
        }
        this
    }
}
