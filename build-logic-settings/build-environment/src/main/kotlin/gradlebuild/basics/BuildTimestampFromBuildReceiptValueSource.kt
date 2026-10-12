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

package gradlebuild.basics

import org.gradle.api.Describable
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Optional
import java.util.Properties


abstract class BuildTimestampFromBuildReceiptValueSource : ValueSource<String, BuildTimestampFromBuildReceiptValueSource.Parameters>, Describable {

    companion object {
        const val BUILD_RECEIPT_FILE_NAME = "build-receipt.properties"
    }

    interface Parameters : ValueSourceParameters {

        val ignoreIncomingBuildReceipt: Property<Boolean>

        @get:Optional
        val buildReceiptFileContents: Property<String>
    }

    override fun obtain(): String? = parameters.run {
        buildReceiptString()
            ?.let { Properties().apply { load(it.reader()) } }
            ?.let { buildReceipt ->
                buildReceipt["buildTimestamp"] as String
            }
    }

    override fun getDisplayName(): String =
        "the build timestamp extracted from the build receipt".let {
            when {
                parameters.ignoreIncomingBuildReceipt.get() -> "$it (ignored)"
                else -> it
            }
        }

    private
    fun Parameters.buildReceiptString(): String? = when {
        ignoreIncomingBuildReceipt.get() -> null
        else -> buildReceiptFileContents.orNull
    }
}
