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
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.text.SimpleDateFormat


class BuildReceipt extends DefaultTask {
    public static final String BUILD_RECEIPT_FILE_NAME = 'build-receipt.properties'

    private static final SimpleDateFormat TIMESTAMP_FORMAT = createTimestampDateFormat()
    private static final SimpleDateFormat ISO_TIMESTAMP_FORMAT = newSimpleDateFormatUTC('yyyy-MM-dd HH:mm:ss z')

    private static final String UNKNOWN_TIMESTAMP = "unknown"

    static SimpleDateFormat createTimestampDateFormat() {
        newSimpleDateFormatUTC('yyyyMMddHHmmssZ')
    }

    static SimpleDateFormat newSimpleDateFormatUTC(String pattern) {
        new SimpleDateFormat(pattern).tap {
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }

    static Properties readBuildReceipt(File dir) {
        File buildReceiptFile = buildReceiptFileIn(dir)
        if (!buildReceiptFile.exists()) {
            throw new GradleException("Can't read build receipt file '$buildReceiptFile' as it doesn't exist")
        }
        buildReceiptFile.withInputStream {
            Properties p = new Properties()
            p.load(it)
            p
        }
    }

    static File buildReceiptFileIn(File dir) {
        new File(dir, BUILD_RECEIPT_FILE_NAME)
    }

    private final ObjectFactory objects

    @Inject
    BuildReceipt(ObjectFactory objects) {
        this.objects = objects
    }

    @Input
    final Property<String> versionNumber = objects.property(String)

    @Input
    final Property<String> baseVersion = objects.property(String)

    @Input
    @Optional
    final Property<String> commitId = objects.property(String)

    @Input
    final Property<Boolean> isSnapshot = objects.property(Boolean)

    @Input
    @Optional
    final Property<Date> buildTimestamp = objects.property(Date)

    @Internal
    File destinationDir

    @OutputFile
    File getReceiptFile() {
        assert destinationDir != null
        buildReceiptFileIn(destinationDir)
    }

    @TaskAction
    void generate() {
        def data = [
            commitId: commitId.getOrElse("HEAD"),
            versionNumber: versionNumber.get(),
            baseVersion: baseVersion.get(),
            isSnapshot: String.valueOf(isSnapshot.get()),
            buildTimestamp: getBuildTimestampAsString(),
            buildTimestampIso: getBuildTimestampAsIsoString(),
        ]

        destinationDir.mkdirs()
        ReproduciblePropertiesWriter.store(data, receiptFile)
    }

    private String getBuildTimestampAsString() {
        return buildTimestamp.getOrNull()?.with { TIMESTAMP_FORMAT.format(it) } ?: UNKNOWN_TIMESTAMP
    }

    private String getBuildTimestampAsIsoString() {
        return buildTimestamp.getOrNull()?.with { ISO_TIMESTAMP_FORMAT.format(it) } ?: UNKNOWN_TIMESTAMP
    }

    void buildTimestampFrom(Provider<String> provider) {
        buildTimestamp.set(provider.map { buildTimestampString ->
            UNKNOWN_TIMESTAMP == buildTimestampString ? null : TIMESTAMP_FORMAT.parse(buildTimestampString)
        })
    }
}
