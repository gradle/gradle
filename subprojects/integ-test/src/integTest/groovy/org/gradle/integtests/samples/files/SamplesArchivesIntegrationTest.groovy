/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.files

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.junit.Rule
import spock.lang.Ignore

class SamplesArchivesIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/files/copy")
    def "can archive a directory"() {
        given:
        executer.inDirectory(sample.dir)
        def archivesDir = sample.dir.file("build/toArchive")
        archivesDir.createDir().file("my-report.pdf").touch()
        archivesDir.createDir().file("numbers.csv").touch()

        and: "A PDF report in a subdirectory of build/toArchive"
        archivesDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds("packageDistribution")

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file("build/dist/my-distribution.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("my-report.pdf").isFile()
        tmpOutDir.file("numbers.csv").isFile()
        tmpOutDir.file("metrics/scatterPlot.pdf").isFile()
    }

    @UsesSample("userguide/files/archivesWithBasePlugin")
    def "can create an archive with a convention-based name"() {
        given:
        executer.inDirectory(sample.dir)
        def archivesDir = sample.dir.file("build/toArchive")
        archivesDir.createDir().file("my-report.pdf").touch()
        archivesDir.createDir().file("numbers.csv").touch()

        and: "A PDF report in a subdirectory of build/toArchive"
        archivesDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds("packageDistribution")

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file("build/distributions/archives-example-1.0.0.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("docs/my-report.pdf").isFile()
        tmpOutDir.file("docs/metrics/scatterPlot.pdf").isFile()
        tmpOutDir.file("numbers.csv").isFile()
    }

    @UsesSample("userguide/files/archives")
    def "can unpack a ZIP file"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds("unpackFiles")

        then:
        def outputDir = sample.dir.file("build/resources")
        outputDir.file("libs/first.txt").isFile()
        outputDir.file("libs/other.txt").isFile()
        outputDir.file("docs.txt").isFile()
    }

    @UsesSample("userguide/files/archivesWithJavaPlugin")
    @Ignore('resolve artifacts eagerly during configuration phase')
    def "can create an uber JAR"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds("uberJar")

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file("build/libs/archives-example-uber-1.0.0.jar")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("META-INF/MANIFEST.MF").isFile()
        tmpOutDir.file("Hello.class").isFile()
        tmpOutDir.file("org/apache/commons/io/IOUtils.class").isFile()
    }

    @UsesSample("userguide/files/sampleJavaProject")
    def "can link tasks via their properties"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds("packageClasses")

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file("build/archives/my-java-project-classes-1.0.0.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("Hello.class").isFile()
    }
}
