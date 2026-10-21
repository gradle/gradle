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

package gradlebuild.xdl

import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Keeps the style/header and Javadoc gates off `xdlCodegen`'s output. The generated facades and
 * carriers are build artifacts regenerated every build, and the schema's `///` doc comments aren't
 * valid Javadoc — so both checks skip anything under `build/generated/xdl/`. Called from the XDL
 * ecosystem conventions so no ecosystem build script repeats it.
 */
fun Project.excludeGeneratedXdlSourcesFromChecks() {
    tasks.withType(Checkstyle::class.java).configureEach {
        exclude { it.file.absolutePath.contains("/generated/xdl/") }
    }
    tasks.withType(Javadoc::class.java).configureEach {
        exclude { it.file.absolutePath.contains("/generated/xdl/") }
    }
}

/**
 * Puts `xdlCodegen`'s output in the `gradle-source-folders` variant, alongside the hand-written
 * source roots under `src/main`.
 *
 * `gradlebuild.unittest-and-compile` fills that variant when it is applied, which is before
 * `xdl-gradle-plugin` adds the codegen output to `main.java` — so the generated facades are in the
 * jar but their source is in no advertised root. The binary compatibility checks look up the source
 * of every API class they report on and fail outright when one cannot be found, which is what a
 * generated facade was.
 *
 * The artifacts carry `xdlCodegen` as their builder: the variant is resolved by tasks in other
 * projects (`:docs:dslMetaData`, `:architecture-test:checkSinceForNonPublicApi`), and a plain
 * directory would leave them reading an output nothing told them to wait for.
 *
 * The layout mirrors `XdlGradlePlugin`, which registers `<outputDir>/java` and
 * `<outputDir>/resources` as source directories under a `build/generated/xdl` convention —
 * the same path this file's check exclusions already match on.
 */
fun Project.publishGeneratedXdlSources() {
    val codegen = tasks.named("xdlCodegen")
    val generated = layout.buildDirectory.dir("generated/xdl")
    configurations.matching { it.name == "transitiveSourcesElements" }.configureEach {
        for (name in listOf("java", "resources")) {
            outgoing.artifact(generated.map { directory -> directory.dir(name) }) {
                builtBy(codegen)
            }
        }
    }
}
