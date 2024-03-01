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

import org.gradle.api.Describable
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Optional
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


abstract class BuildTimestampValueSource : ValueSource<String, BuildTimestampValueSource.Parameters>, Describable {

    interface Parameters : ValueSourceParameters {

        @get:Optional
        val buildTimestampFromBuildReceipt: Property<String>

        @get:Optional
        val buildTimestampFromGradleProperty: Property<String>

        val runningOnCi: Property<Boolean>

        val runningInstallTask: Property<Boolean>
        val runningDocsTestTask: Property<Boolean>
    }

    override fun obtain(): String? = parameters.run {

        val buildTimestampFromReceipt = buildTimestampFromBuildReceipt.orNull
        if (buildTimestampFromReceipt != null) {
            println("Using timestamp from incoming build receipt: $buildTimestampFromReceipt")
            return buildTimestampFromReceipt
        }

        val timestampFormat = SimpleDateFormat("yyyyMMddHHmmssZ").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val buildTimestampFromProperty = buildTimestampFromGradleProperty.orNull
        val buildTime = when {
            buildTimestampFromProperty != null -> {
                timestampFormat.parse(buildTimestampFromProperty)
            }
            runningInstallTask.get() || runningDocsTestTask.get() || runningOnCi.get() -> {
                Date()
            }
            else -> {
                Date().withoutTime()
            }
        }
        return timestampFormat.format(buildTime)
    }

    override fun getDisplayName(): String =
        "the build timestamp ($timestampSource)"

    private
    val timestampSource: String
        get() = parameters.run {
            when {
                buildTimestampFromBuildReceipt.isPresent -> "from build receipt"
                buildTimestampFromGradleProperty.isPresent -> "from buildTimestamp property"
                runningInstallTask.get() -> "from current time because installing"
                runningDocsTestTask.get() -> "from current time because testing docs"
                runningOnCi.get() -> "from current time because CI"
                else -> "from current date"
            }
        }

    private
    fun Date.withoutTime(): Date = SimpleDateFormat("yyyy-MM-dd").run {
        parse(format(this@withoutTime))
    }
}
