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

package gradlebuild.packageinfo.support

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gradlebuild.packageinfo.model.AggregatedPackageInfoData
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import java.io.File

/**
 * The `package-info.java` files named by an aggregated package-info JSON, resolved against [settingsDir].
 *
 * The JSON is parsed inside a provider mapping, so the read stays lazy and happens only once [aggregate]'s producer
 * task has run — which also makes the returned collection carry that task dependency. Declaring the result as an
 * input of a consuming task is what makes edits to a `package-info.java` invalidate that task: the aggregate itself
 * only names the files, so content changes are invisible to it.
 *
 * Must not be queried at configuration time.
 */
fun packageInfoFilesFrom(objects: ObjectFactory, settingsDir: Directory, aggregate: Provider<RegularFile>): FileCollection =
    objects.fileCollection().from(
        aggregate.map { json -> packageInfoPathsFrom(json.asFile).map { settingsDir.file(it) } }
    )

private fun packageInfoPathsFrom(aggregate: File): List<String> {
    val type = object : TypeToken<AggregatedPackageInfoData>() {}.type
    val data: AggregatedPackageInfoData = aggregate.reader().use { Gson().fromJson(it, type) }
    return data.values.flatMap { entry -> entry.packageInfo.map { it.path } }.distinct()
}
