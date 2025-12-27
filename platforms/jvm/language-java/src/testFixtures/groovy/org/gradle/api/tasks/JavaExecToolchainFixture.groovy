/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.internal.jvm.inspection.MetadataProbe
import org.gradle.test.fixtures.file.TestFile

class JavaExecToolchainFixture {
    static TestFile writeJavaWrapperThatCannotBeProbed(TestFile dir, File javaExecutable) {
        def javaHome = dir.file("javaWrapperHome")
        def binDir = javaHome.file("bin")
        def javaWrapper = binDir.file("java") << """#!/bin/bash
            CLASS_NAME="\${@: -1}"

            if [ -z "\$CLASS_NAME" ]; then
                echo "Could not determine class name" >&2
                exit 127
            elif [ "\$CLASS_NAME" = "${MetadataProbe.PROBE_CLASS_NAME}" ]; then
                echo "Inhibiting metadata probe" >&2
                exit 0
            fi

            exec ${javaExecutable.absolutePath} "\$@"
        """.stripMargin()
        javaWrapper.setExecutable(true)
        return javaWrapper
    }
}
