/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
class BuildReceipt extends DefaultTask {
    private static final TIMESTAMP_FORMAT = new java.text.SimpleDateFormat('yyyyMMddHHmmssZ')
    private static final String UNKNOWN_TIMESTAMP = "unknown"

    @Input
    String versionNumber
    @Input
    String baseVersion
    @Input
    @Optional
    String commitId
    @Input
    boolean snapshot
    @Input
    @Optional
    Date buildTimestamp
    @OutputFile
    File receiptFile

    @TaskAction
    void generate() {
        def data = [
            commitId: commitId ?: "HEAD",
            versionNumber: versionNumber,
            baseVersion: baseVersion,
            isSnapshot: String.valueOf(snapshot),
            buildTimestamp: getBuildTimestampAsString(),
        ]

        receiptFile.parentFile.mkdirs()
        ReproduciblePropertiesWriter.store(data, receiptFile)
    }

    private String getBuildTimestampAsString() {
        buildTimestamp ? TIMESTAMP_FORMAT.format(buildTimestamp) : UNKNOWN_TIMESTAMP
    }

    void setBuildTimestamp(String buildTimestampString) {
        this.buildTimestamp = UNKNOWN_TIMESTAMP == buildTimestampString ? null : TIMESTAMP_FORMAT.parse(buildTimestampString)
    }
}
