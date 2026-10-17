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

package gradlebuild.packageinfo.model

const val PACKAGE_INFO_FILE_NAME = "package-info.java"

/**
 * The package data contributed by a single project, as written by `GeneratePackageInfoDataTask`.
 *
 * Paths are relative to [projectDir] so that the per-project data does not depend on where the project sits in the
 * build; the aggregator rebases them onto [projectDir].
 */
data class ProjectPackageInfoData(
    val project: String,
    val projectDir: String,
    /** Package name to the `package-info.java` files declaring it. Empty list means the package has none. */
    val packages: Map<String, List<String>>,
)

/**
 * The build-wide package data, as written by `AggregatePackageInfoDataTask`: package name to [PackageEntry].
 *
 * Consumed by `PackageInfoTest` in `:architecture-test`. Kept in sync by hand with the reader there, since build
 * logic is not on the test's classpath.
 */
typealias AggregatedPackageInfoData = Map<String, PackageEntry>

/**
 * Everything known about one package across the whole build.
 *
 * [projects] lists every project contributing sources to the package, whether or not it declares a `package-info.java`.
 * More than one entry means the package is split across projects.
 */
data class PackageEntry(
    val projects: List<String>,
    val packageInfo: List<PackageInfoFile>,
)

/** A single `package-info.java`, at a path relative to the settings directory. */
data class PackageInfoFile(
    val project: String,
    val path: String,
)
