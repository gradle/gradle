import java.util.Properties

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

/**
 * The Groovy major version bundled by Gradle, overridable via `-DbundleGroovyMajor` for
 * Gradleception coverage builds.
 *
 * Default should match the Groovy version declared in `gradle/dependency-management/shared-versions.properties`.
 */
val defaultGroovyMajor = 4
val bundleGroovyMajor = providers.systemProperty("bundleGroovyMajor").map(String::toInt).getOrElse(defaultGroovyMajor)

val basePath = run {
    val root = if (rootProject.name.startsWith("build-logic")) {
        layout.rootDirectory.dir("..")
    } else {
        layout.rootDirectory
    }
    root.dir("gradle").dir("dependency-management")
}

/**
 * Version-catalog aliases that must change together when bundling a non-default Groovy major,
 * loaded from `groovy-versions.properties` (keyed as `groovy<major>.<alias>`).
 *
 * CodeNarc is intentionally excluded: it has no Groovy 5 build, and its analysis runtime is
 * independent of the Groovy version bundled into the distribution.
 */
val groovyMajorOverrides: Map<String, String> = loadGroovyMajorOverrides()

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            applyGroovyMajorOverrides()
            from(files(basePath.file("distribution.versions.toml")))
        }
        create("providedLibs") {
            from(files(basePath.file("provided.versions.toml")))
        }
        create("testLibs") {
            applyGroovyMajorOverrides()
            from(files(basePath.file("test.versions.toml")))
        }
        create("buildLibs") {
            from(files(basePath.file("build.versions.toml")))
        }
        val sharedVersions = basePath.file("shared-versions.properties").asFile.inputStream().use {
            Properties().apply { load(it) }
        }
        all {
            sharedVersions.forEach { key, value ->
                version(key.toString(), value.toString())
            }
        }
    }
}

/**
 * Loads the [groovyMajorOverrides] for [bundleGroovyMajor] from `groovy-versions.properties`,
 * selecting entries keyed `groovy<major>.<alias>`. The default major needs no overrides, as its
 * versions already come from the imported TOML catalogs.
 */
fun loadGroovyMajorOverrides(): Map<String, String> {
    if (bundleGroovyMajor == defaultGroovyMajor) {
        return emptyMap()
    }
    val prefix = "groovy$bundleGroovyMajor."
    val groovyVersions = basePath.file("groovy-versions.properties").asFile.inputStream().use {
        Properties().apply { load(it) }
    }
    val overrides = groovyVersions.entries
        .filter { (key, _) -> key.toString().startsWith(prefix) }
        .associate { (key, value) -> key.toString().removePrefix(prefix) to value.toString() }
    require(overrides.isNotEmpty()) {
        "Unsupported bundled Groovy major version: $bundleGroovyMajor"
    }
    return overrides
}

/**
 * Applies [groovyMajorOverrides] to a catalog that feeds the Gradle distribution.
 *
 * Deliberately not applied to `buildLibs`: build-logic runs on the bootstrap Gradle and
 * compiles against `localGroovy()`, and its CodeNarc has no Groovy 5 build, so swapping only
 * its Groovy and Spock would leave it on an inconsistent mix.
 *
 * Must be called before `from()`, because `version()` is first-wins and the imported TOML is
 * parsed once the catalog is built.
 */
fun VersionCatalogBuilder.applyGroovyMajorOverrides() {
    groovyMajorOverrides.forEach { (alias, version) ->
        version(alias, version)
    }
}
