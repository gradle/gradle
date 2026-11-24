/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.fixtures

import java.io.File


fun projectRootOf(scriptFile: File, importedProjectRoot: File, stopAt: File? = null): File {

    // TODO remove hardcoded reference to settings.gradle once there's a public TAPI client api for that
    fun isProjectRoot(dir: File) =
        dir.name == "buildSrc"
            || File(dir, "settings.gradle.kts").isFile
            || File(dir, "settings.gradle").isFile

    tailrec fun test(dir: File): File =
        when {
            dir == importedProjectRoot -> importedProjectRoot
            isProjectRoot(dir) -> dir
            else -> {
                when (val parentDir = dir.parentFile) {
                    null, dir, stopAt -> scriptFile.parentFile // external project
                    else -> test(parentDir)
                }
            }
        }

    return test(scriptFile.parentFile)
}
