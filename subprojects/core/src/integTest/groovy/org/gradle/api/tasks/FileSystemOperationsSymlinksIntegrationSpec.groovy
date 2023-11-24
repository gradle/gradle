/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

class FileSystemOperationsSymlinksIntegrationSpec extends AbstractFileSystemCopySymlinksIntegrationSpec {

    private TestFile outputDir;

    @Override
    String constructBuildScript(String inputConfig, String mainPath = "") {
        outputDir = file("output")

        """
        // configuration cache compatibility
        interface Injected {
            @get:Inject
            val operations: FileSystemOperations
        }

        tasks.register("$mainTask") {
            val fs = project.objects.newInstance<Injected>().operations
            doLast {
                fs.copy {
                    from("${inputDirectory.name}/$mainPath")
                    into("${outputDir.name}")

                    $inputConfig
                }
            }
        }
        """
    }

    @Override
    TestFile getResultDir() {
        return outputDir
    }

}
