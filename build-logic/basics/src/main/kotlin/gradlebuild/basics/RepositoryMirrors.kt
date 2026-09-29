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

package gradlebuild.basics

import java.io.File


// TestKit builds never see the settings-level mirror script, so pass it as an init script instead.
object RepositoryMirrors {

    private
    const val PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY = "org.gradle.internal.plugins.portal.url.override"

    private
    const val PLUGIN_PORTAL_MIRROR_NAME = "gradle-prod-plugins"

    private
    const val MIRRORS_SCRIPT = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts"

    private
    val mirrorUrls: Map<String, String> by lazy {
        if (System.getenv("IGNORE_MIRROR")?.toBoolean() == true) {
            emptyMap()
        } else {
            System.getenv("REPO_MIRROR_URLS")?.ifBlank { null }?.split(',')?.associate { nameToUrl ->
                val (name, url) = nameToUrl.split(':', limit = 2)
                name to url
            } ?: emptyMap()
        }
    }

    @JvmStatic
    fun testKitArguments(): List<String> {
        if (mirrorUrls.isEmpty()) {
            return emptyList()
        }
        val arguments = mutableListOf("--init-script", mirrorsScript().absolutePath)
        mirrorUrls[PLUGIN_PORTAL_MIRROR_NAME]?.let {
            arguments += "-D$PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY=$it"
        }
        return arguments
    }

    private
    fun mirrorsScript(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { it.resolve(MIRRORS_SCRIPT) }
            .firstOrNull { it.isFile }
            ?: throw IllegalStateException("REPO_MIRROR_URLS is set but $MIRRORS_SCRIPT was not found above ${File("").absolutePath}")
}
