/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.internal.sharedruntime.codegen


val fileHeader: String
    get() = fileHeaderFor(kotlinDslPackageName)


fun fileHeaderFor(packageName: String, isIncubating: Boolean = false) =
    """$licenseHeader

@file:Suppress(
    "unused",
    "nothing_to_inline",
    "useless_cast",
    "unchecked_cast",
    "extension_shadowed_by_member",
    "redundant_projection",
    "RemoveRedundantBackticks",
    "ObjectPropertyName",
    "deprecation",
    "detekt:all"
)
@file:org.gradle.api.Generated${if (isIncubating) "\n@file:org.gradle.api.Incubating" else ""}

package $packageName
"""

@Suppress("TopLevelPropertyNaming") // TODO: renaming leads to "Unresolved reference" error at use sites... why is that?
const val kotlinDslPackageName = "org.gradle.kotlin.dsl"


@Suppress("TopLevelPropertyNaming") // TODO: renaming leads to "Unresolved reference" error at use sites... why is that?
const val kotlinDslPackagePath = "org/gradle/kotlin/dsl"


@Suppress("TopLevelPropertyNaming") // TODO: renaming leads to "Unresolved reference" error at use sites... why is that?
const val licenseHeader = """/*
 * Copyright 2018 the original author or authors.
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
 */"""
