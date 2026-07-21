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

package gradlebuild.xdcl

import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Keeps the style/header and Javadoc gates off `xdclCodegen`'s output. The generated facades and
 * carriers are build artifacts regenerated every build, and the schema's `///` doc comments aren't
 * valid Javadoc — so both checks skip anything under `build/generated/xdcl/`. Called from the XDCL
 * ecosystem conventions so no ecosystem build script repeats it.
 */
fun Project.excludeGeneratedXdclSourcesFromChecks() {
    tasks.withType(Checkstyle::class.java).configureEach {
        exclude { it.file.absolutePath.contains("/generated/xdcl/") }
    }
    tasks.withType(Javadoc::class.java).configureEach {
        exclude { it.file.absolutePath.contains("/generated/xdcl/") }
    }
}
