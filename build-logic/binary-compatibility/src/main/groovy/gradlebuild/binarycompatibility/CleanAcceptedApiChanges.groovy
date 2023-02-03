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

package gradlebuild.binarycompatibility

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * A task used for cleaning up all accepted API changes. The functionality is called whenever the release process initiates "branching".
 */
@DisableCachingByDefault(because = "Not worth caching")
class CleanAcceptedApiChanges extends DefaultTask {

    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputFile
    File jsonFile

    @TaskAction
    void clean() {
        AcceptedApiChangesJsonFileManager jsonFileManager = new AcceptedApiChangesJsonFileManager()
        jsonFileManager.emptyAcceptedApiChanges(jsonFile)
    }
}
