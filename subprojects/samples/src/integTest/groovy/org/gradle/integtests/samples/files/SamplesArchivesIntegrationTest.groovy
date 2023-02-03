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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class SamplesArchivesIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("files/copy")
    def "can archive a directory with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def archivesDir = dslDir.file("build/toArchive")
        archivesDir.createDir().file("my-report.pdf").touch()
        archivesDir.createDir().file("numbers.csv").touch()

        and: "A PDF report in a subdirectory of build/toArchive"
        archivesDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds("packageDistribution")

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file("build/dist/my-distribution.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("my-report.pdf").isFile()
        tmpOutDir.file("numbers.csv").isFile()
        tmpOutDir.file("metrics/scatterPlot.pdf").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/archivesWithBasePlugin")
    def "can create an archive with a convention-based name with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def archivesDir = dslDir.file("build/toArchive")
        archivesDir.createDir().file("my-report.pdf").touch()
        archivesDir.createDir().file("numbers.csv").touch()

        and: "A PDF report in a subdirectory of build/toArchive"
        archivesDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds("packageDistribution")

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file("build/distributions/archives-example-1.0.0.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("docs/my-report.pdf").isFile()
        tmpOutDir.file("docs/metrics/scatterPlot.pdf").isFile()
        tmpOutDir.file("numbers.csv").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/archives")
    def "can unpack a ZIP file with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds("unpackFiles")

        then:
        def outputDir = dslDir.file("build/resources")
        outputDir.file("libs/first.txt").isFile()
        outputDir.file("libs/other.txt").isFile()
        outputDir.file("docs.txt").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/archives")
    def "can unpack a part of a ZIP file with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds("unpackLibsDirectory")

        then:
        def outputDir = dslDir.file("build/resources")
        outputDir.file("libs/first.txt").assertDoesNotExist()
        outputDir.file("libs/other.txt").assertDoesNotExist()
        outputDir.file("docs.txt").assertDoesNotExist()
        outputDir.file("first.txt").isFile()
        outputDir.file("other.txt").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/archivesWithJavaPlugin")
    def "can create an uber JAR with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds("uberJar")

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file("build/libs/archives-example-1.0.0-uber.jar")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("META-INF/MANIFEST.MF").isFile()
        tmpOutDir.file("Hello.class").isFile()
        tmpOutDir.file("org/apache/commons/io/IOUtils.class").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/sampleJavaProject")
    def "can link tasks via their properties with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds("packageClasses")

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file("build/archives/my-java-project-classes-1.0.0.zip")
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file("Hello.class").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
}
