/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.integrationtests.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity


/**
 * Verifies the correct behavior of a feature, as opposed to just a small unit of code.
 * Usually referred to as 'functional tests' in literature, but our code base has historically
 * been using the term 'integration test'.
 */
@CacheableTask
abstract class IntegrationTest : DistributionTest() {

    override val prefix = if (name.contains("CrossVersion")) "crossVersion" else "integ"

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val samplesDir = gradleInstallationForTest.gradleSnippetsDir

    override fun setClasspath(classpath: FileCollection) {
        /*
         * The 'kotlin-daemon-client.jar' repackages 'native-platform' with all its binaries.
         * Here we make sure it is placed at the end of the test classpath so that we do not accidentally
         * pick parts of 'native-platform' from the 'kotlin-daemon-client.jar' when instantiating
         * a Gradle runner.
         */
        val reorderedClasspath = classpath.filter { file ->
            !file.name.startsWith("kotlin-daemon-client")
        }.plus(classpath.filter { it.name.startsWith("kotlin-daemon-client") })
        super.setClasspath(reorderedClasspath)
    }
}
