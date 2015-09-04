/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.javascript

import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.RelativeFile
import spock.lang.Specification

class JavaScriptCompileDestinationCalculatorTest extends Specification {
    File outputDir = new File("/path/to/output")
    JavaScriptCompileDestinationCalculator calculator = new JavaScriptCompileDestinationCalculator(outputDir)

    def "calculates correct destination for javascript file"() {
        when:
        File inputFile = new File("/some/input/javascript/${fileName}")
        RelativeFile relativeInputFile = new RelativeFile(inputFile, new RelativePath(true, "javascript", fileName))

        then:
        calculator.transform(relativeInputFile) == new File("/path/to/output/javascript/${minFileName}")

        where:
        fileName      | minFileName
        "file.js"     | "file.min.js"
        "file.max.js" | "file.max.min.js"
        ".js"         | ".min.js"
        "no-ext"      | "no-ext.min"
    }
}
