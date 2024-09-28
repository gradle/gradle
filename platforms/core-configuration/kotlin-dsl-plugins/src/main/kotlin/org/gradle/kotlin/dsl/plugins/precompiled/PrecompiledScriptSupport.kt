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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.internal.catalog.DefaultVersionCatalog
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

fun serializeVersionCatalog(versionCatalog: DefaultVersionCatalog): String {
    val bytes = ByteArrayOutputStream().use { byteStream ->
        ObjectOutputStream(GZIPOutputStream(byteStream)).use { oos ->
            oos.writeObject(DefaultVersionCatalog(
                versionCatalog.name,
                versionCatalog.description,
                versionCatalog.libraryAliases.associateWith { versionCatalog.getDependencyData(it) },
                versionCatalog.bundleAliases.associateWith { versionCatalog.getBundle(it) },
                versionCatalog.versionAliases.associateWith { versionCatalog.getVersion(it) },
                versionCatalog.pluginAliases.associateWith { versionCatalog.getPlugin(it) },
            ))
        }
        byteStream.toByteArray()
    }
    return Base64.getEncoder().encodeToString(bytes)
}
