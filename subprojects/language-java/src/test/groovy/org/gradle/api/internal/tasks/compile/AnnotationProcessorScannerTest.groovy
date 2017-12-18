/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.incap.IncapVersionDetector
import org.gradle.internal.file.FileType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JarUtils
import org.junit.Rule
import spock.lang.Specification

class AnnotationProcessorScannerTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def scanner = new AnnotationProcessorScanner()

    def "finds incremental annotation processor from jar"() {
        given:
        def jar = tmpDir.file("incrementalAP.jar")
        jar << JarUtils.jarWithContents(
            "META-INF/services/javax.annotation.processing.Processor": "thing",
            "META-INF/org.gradle.incap": "simple")
        def info = scanner.calculate(jar, FileType.RegularFile)

        expect:
        info.name == "thing"
        info.isProcessor()
        info.isIncrementalEnabled()
    }

    def "finds incremental annotation processor from directory"() {
        given:
        tmpDir.file("incrementalAP/META-INF/services/javax.annotation.processing.Processor") << "thing"
        tmpDir.file("incrementalAP/${IncapVersionDetector.META_INF_INCAP}") << "simple"
        def info = scanner.calculate(tmpDir.file("incrementalAP"), FileType.Directory)

        expect:
        info.name == "thing"
        info.isProcessor()
        info.isIncrementalEnabled()
    }

    def "does not mark non-incremental processor as incremental"() {
        given:
        tmpDir.file("incrementalAP/META-INF/services/javax.annotation.processing.Processor") << "thing"
        def info = scanner.calculate(tmpDir.file("incrementalAP"), FileType.Directory)

        expect:
        info.name == "thing"
        info.isProcessor()
        !info.isIncrementalEnabled()
    }
}
