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

package gradlebuild.packaging.tasks

import java.io.File


private
val KOTLIN_MODULE_PATH = Regex("META-INF/.*\\.kotlin_module")


internal
enum class ContentFilter {
    VERBATIM,
    API_ONLY,
    SKIP
}


/**
 * What to do with [file] of a classes directory [classDir].
 */
internal
fun contentFilterFor(classDir: File, file: File): ContentFilter =
    contentFilterFor(file.relativeTo(classDir).path)


/**
 * What to do with the entry at [relativePath] of a classes directory.
 *
 * The path takes the separator of either operating system. The rules below need `/`, and
 * a relative path on Windows comes with `\`.
 */
internal
fun contentFilterFor(relativePath: String): ContentFilter {
    val path = relativePath.replace('\\', '/')
    // Extraction keeps the module and the package annotations, so module-info and
    // package-info need no copy of their own
    if (path.endsWith(".class")) {
        return ContentFilter.API_ONLY
    }
    if (path == "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule") {
        return ContentFilter.VERBATIM
    }
    if (path == "META-INF/services/org.codehaus.groovy.transform.ASTTransformation") {
        return ContentFilter.VERBATIM
    }
    if (path.matches(KOTLIN_MODULE_PATH)) {
        return ContentFilter.VERBATIM
    }
    return ContentFilter.SKIP
}
