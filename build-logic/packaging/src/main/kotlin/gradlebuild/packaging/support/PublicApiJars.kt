/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.packaging.support

import gradlebuild.packaging.support.ArtifactViewHelper.lenientProjectArtifactReselection
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Category
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar

/**
 * The API stubs (signature-only `.class` files) reselected from every project on [classpath].
 *
 * Shared by the ABI jar production (`gradlebuild.public-api-jar`) and its publication
 * (`gradlebuild.public-api-publish`) so both extract the same surface from the same classpath.
 */
fun Project.publicApiAbiStubs(classpath: Provider<ResolvableConfiguration>): Provider<FileCollection> =
    lenientProjectArtifactReselection(
        classpath,
        { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, "api-stubs")) }
    )

/**
 * Assembles the public API ABI [stubs] into this jar. Duplicate `package-info.class` across modules
 * is expected and safe (guarded by PackageInfoTest).
 */
fun Jar.includePublicApiAbiStubs(stubs: Provider<FileCollection>) {
    from(stubs) {
        include("**/*.class")
        include("META-INF/*.kotlin_module")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
