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


private
val KOTLIN_MODULE_PATH = Regex("META-INF/.*\\.kotlin_module")


internal
enum class ContentFilter {
    VERBATIM,
    API_ONLY,
    SKIP
}


/**
 * What to do with the entry at [relativePath] of a classes directory.
 *
 * The caller gives a path with `/` as separator on every operating system.
 */
internal
fun contentFilterFor(relativePath: String): ContentFilter {
    // The extractor includes the package-private members, so it keeps module-info and
    // package-info with the module directives and the package annotations of those files
    if (relativePath.endsWith(".class")) {
        return ContentFilter.API_ONLY
    }
    if (relativePath == "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule") {
        return ContentFilter.VERBATIM
    }
    if (relativePath == "META-INF/services/org.codehaus.groovy.transform.ASTTransformation") {
        return ContentFilter.VERBATIM
    }
    if (relativePath.matches(KOTLIN_MODULE_PATH)) {
        return ContentFilter.VERBATIM
    }
    return ContentFilter.SKIP
}
