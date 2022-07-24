/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.swift

import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.test.fixtures.file.ExecOutput


class SwiftPmRunner {
    private final AvailableToolChains.InstalledSwiftc swiftc
    private File projectDir
    private List<String> args = []

    static SwiftPmRunner create(AvailableToolChains.InstalledSwiftc swiftc) {
        return new SwiftPmRunner(swiftc)
    }

    private SwiftPmRunner(AvailableToolChains.InstalledSwiftc swiftc) {
        this.swiftc = swiftc
    }

    SwiftPmRunner withProjectDir(File projectDir) {
        this.projectDir = projectDir.canonicalFile
        return this
    }

    SwiftPmRunner withArguments(String... args) {
        this.args.clear()
        this.args.addAll(args as List)
        return this
    }

    ExecOutput build() {
        def result = run()
        if (result.exitCode != 0) {
            throw new AssertionError("Swift PM exited with non-zero exit code. Output:\n${result.out}")
        }
        return result
    }

    ExecOutput buildAndFails() {
        def result = run()
        if (result.exitCode == 0) {
            throw new AssertionError("Swift PM unexpectedly succeeded. Output:\n${result.out}")
        }
        return result
    }

    private ExecOutput run() {
        assert projectDir != null
        def builder = new ProcessBuilder()
        builder.command([swiftc.tool("swift").absolutePath] + args)
        println "Running " + builder.command()
        builder.directory(projectDir)
        builder.redirectErrorStream(true)
        swiftc.runtimeEnv.each {
            def var = it.split("=")
            builder.environment().put(var[0], var[1])
        }
        def process = builder.start()
        process.outputStream.close()
        def output = process.inputStream.text
        println output
        int exitCode = process.waitFor()
        return new ExecOutput(exitCode, output, "")
    }
}
