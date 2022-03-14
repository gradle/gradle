/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.testcleanup.extension

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File


/**
 * An extension to work with {@see TestFilesCleanupService}.
 * We have to collect all information we need in this extension and pass them
 * to the build service.
 */
interface TestFilesCleanupBuildServiceRootExtension {
    val projectStates: MapProperty<String, TestFilesCleanupProjectState>

    /**
     * Whether current build is a "clean up runner step" on CI.
     */
    val cleanupRunnerStep: Property<Boolean>

    /**
     * Key is the path of a task, value is the possible report dirs it generates.
     */
    val taskPathToReports: MapProperty<String, List<File>>
}
