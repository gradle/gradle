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
package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer
import org.gradle.internal.os.OperatingSystem
import org.gradle.platform.internal.DefaultBuildPlatform

/**
 * This normalizer converts toolchain error message so it doesn't contain platform-specific information.
 */
class PlatformInOutputNormalizer implements OutputNormalizer {
    def static internalOs = OperatingSystem.current()
    def static os = DefaultBuildPlatform.getOperatingSystem(internalOs)
    def static arch = getArchitectureString()

    private static getArchitectureString() {
        def archProperty = System.getProperty("os.arch")
        if (archProperty == "amd64") {
            return "x86_64"
        } else {
            return archProperty
        }
    }

    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return commandOutput
            .replaceAll(arch, "%ARCH%")
            .replaceAll(os.name(), "%OS%")
            .replaceAll(internalOs.toString() ,"%OS_ARCH%")
    }
}
