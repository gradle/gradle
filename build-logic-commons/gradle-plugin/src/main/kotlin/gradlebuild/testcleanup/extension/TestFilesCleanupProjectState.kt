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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property


/**
 * Works with {@see TestFilesCleanupService} and {@see TestFilesCleanupServiceRootExtension}.
 * It collects states to be used in the build service for each project.
 */
interface TestFilesCleanupProjectState : TestFileCleanUpExtension {
    val projectPath: Property<String>
    val projectBuildDir: DirectoryProperty
}
