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

package gradlebuild.identity.provider

import com.google.gson.Gson
import org.gradle.api.Describable
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.net.URI


abstract class LatestNightlyVersionValueSource
    : ValueSource<String, LatestNightlyVersionValueSource.Parameters>, Describable {

    interface Parameters : ValueSourceParameters {
        // Empty
    }

    override fun obtain(): String? = parameters.run {
        val url = URI("https://services.gradle.org/versions/nightly")
        return url.toURL().openStream().use { stream ->
            val content = stream.bufferedReader().readText()
            Gson().fromJson(content, Map::class.java)["version"]!!.toString()
        }
    }

    override fun getDisplayName(): String =
        "the version of the latest published nightly"
}
