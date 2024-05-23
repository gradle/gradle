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

import gradle.kotlin.dsl.accessors._13687bf53899272e089ef07734aa6040.check
import gradle.kotlin.dsl.accessors._13687bf53899272e089ef07734aa6040.checkstyle
import gradle.kotlin.dsl.accessors._13687bf53899272e089ef07734aa6040.codenarc
import gradle.kotlin.dsl.accessors._caaef686956ef05d8c7d73205bf1c4b7.detekt
import groovy.lang.GroovySystem
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.util.internal.VersionNumber
import javax.inject.Inject

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

// Configures javadoc task for java projects, ensuring javadoc is compliant.
// Javadoc is generated for private classes and methods, and files are allowed to omit javadoc.
// These requirements are different than those of the public javadoc.
// This does not configure the public Javadoc published to the website

pluginManager.withPlugin("gradlebuild.code-quality") {
    tasks {
        named("codeQuality") {
            dependsOn(tasks.withType<Javadoc>())
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    assert(name != "javadocAll") // This plugin should not be applied to the :docs project.

    onlyIf("Do not run the task if there are no java sources") {
        // Javadoc task will complain if we only have package-info.java files and no
        // other java files (as is with some Kotlin projects)
        !source.matching {
            exclude("**/package-info.java")
        }.isEmpty
    }

    options {
        this as StandardJavadocDocletOptions

        // Enable all javadoc warnings, except for:
        // - missing: Classes and methods are not required to have javadoc
        // - reference: We allow references to classes that are not part of the compilation
        addBooleanOption("Xdoclint:all,-missing,-reference", true)

        // Add support for custom tags
        tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:")

        // Process all source files for valid javadoc, not just public ones
        addBooleanOption("private", true)
        addBooleanOption("package", true)
    }
}
