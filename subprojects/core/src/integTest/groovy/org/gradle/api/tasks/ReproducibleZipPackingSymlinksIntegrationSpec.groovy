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

class ReproducibleZipPackingSymlinksIntegrationSpec extends AbstractFileSystemCopySymlinksIntegrationSpec {

    @Override
    String constructBuildScript(String inputConfig, String mainPath = "") {
        """
        tasks.register<Zip>("$mainTask") {
            archiveFileName.set("test.zip")
            destinationDirectory.set(file("temp-dir"))
            setReproducibleFileOrder(true)

            from("${inputDirectory.name}/$mainPath")
            $inputConfig
        }
        """
    }

    @Override
    TestFile getResultDir() {
        def outputDir = createDir("output")
        def outputArchive = file("temp-dir/test.zip")
        if (outputArchive.exists()) {
            outputArchive.usingNativeTools().unzipTo(outputDir)
        }
        return outputDir
    }

}
