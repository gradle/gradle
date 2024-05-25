/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion


interface MinifiedApplicationJar {
    /**
     * The minified JAR output.
     */
    val minifiedJar: Provider<RegularFile>

    /**
     * Use the given function to calculate the base name of the minified JAR, given the target Gradle version.
     */
    fun outputJarName(name: (GradleVersion) -> String)

    /**
     * Used in the minified JAR manifest.
     */
    val implementationTitle: Property<String>

    /**
     * Main class name for the application.
     */
    val mainClassName: Property<String>

    /**
     * Excludes the given resources from the minified JAR.
     */
    fun exclude(pattern: String)

    /**
     * Excludes the given resources from dependencies merged into the minified JAR.
     */
    fun excludeFromDependencies(pattern: String)
}
