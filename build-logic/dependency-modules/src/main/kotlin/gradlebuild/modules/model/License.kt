/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.modules.model


/**
 * Canonical representation of an open-source license used by a Gradle distribution component.
 *
 * Each entry carries:
 * - [displayName] — the human-readable name used in the generated LICENSE file
 * - [url] — the canonical, https:// URL for the license text
 * - [aliases] — all known POM `<licenses><license><name>` strings that map to this license;
 *   used by [GenerateLicenseFile] to normalise inconsistent spellings across the Maven ecosystem
 *
 * ## Adding a new dependency with an unrecognised license
 *
 * If `./gradlew generateLicenseFile` fails with "declare a license name not registered in
 * License.kt", add the raw POM name string to the [aliases] list of the matching entry, or
 * create a new entry if the license is genuinely new.
 *
 * ## Adding a dependency whose POM has no license data
 *
 * If the task fails with "no license data in their POM or any parent POM", add a hardcoded
 * entry to the `hardcodedLicenses` map in `GenerateLicenseFile.kt`.
 */
enum class License(
    val displayName: String,
    val url: String,
    val aliases: List<String> = emptyList(),
) {
    Apache2(
        "Apache License, Version 2.0",
        "https://www.apache.org/licenses/LICENSE-2.0",
        listOf(
            "Apache License, Version 2.0",
            "The Apache Software License, Version 2.0",
            "The Apache License, Version 2.0",
            "Apache Software License - Version 2.0",
            "Apache License 2.0",
            "Apache-2.0",
            "Apache 2.0",
            "Apache 2",
            "ASL, version 2",
        ),
    ),
    BSD3(
        "3-Clause BSD License",
        "https://opensource.org/licenses/BSD-3-Clause",
        listOf(
            "BSD 3-Clause License",
            "BSD-3-Clause",
            "New BSD License",
            "The New BSD License",
            "BSD",
            "Revised BSD",
            "The BSD License",
        ),
    ),
    BouncyCastle(
        "Bouncy Castle Licence",
        "https://www.bouncycastle.org/licence.html",
        listOf("Bouncy Castle Licence"),
    ),
    EPL(
        "Eclipse Public License 1.0",
        "https://www.eclipse.org/legal/epl-v10.html",
        listOf("EPL-1.0", "Eclipse Public License v1.0"),
    ),
    EPL2(
        "Eclipse Public License 2.0",
        "https://www.eclipse.org/legal/epl-v20.html",
        listOf("EPL-2.0", "Eclipse Public License v2.0"),
    ),
    LGPL21(
        "LGPL 2.1",
        "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
        listOf(
            "GNU Lesser General Public License",
            "GNU Lesser General Public License, Version 2.1",
            "GNU Lesser General Public License, version 2.1",
            "LGPL-2.1-or-later",
        ),
    ),
    MIT(
        "MIT License",
        "https://opensource.org/licenses/MIT",
        listOf("The MIT License", "MIT License", "MIT"),
    );

    companion object {
        /** Lookup map built lazily from all [aliases] across all entries. */
        val byPomName: Map<String, License> by lazy {
            entries.flatMap { lic -> lic.aliases.map { alias -> alias to lic } }.toMap()
        }

        /** Returns the [License] whose [aliases] include [name], or null if unrecognised. */
        fun fromPomName(name: String): License? = byPomName[name]
    }
}
