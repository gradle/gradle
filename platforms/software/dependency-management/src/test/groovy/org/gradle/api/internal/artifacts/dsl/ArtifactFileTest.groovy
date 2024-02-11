/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import spock.lang.Specification

class ArtifactFileTest extends Specification {
    def "determines name and extension from file name #inputFileName with version #version"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), version)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == null

        where:
        inputFileName       | name              | extension | version
        "some-file.zip"     | "some-file"       | "zip"     | "1.2"
        "some-file.zip.zip" | "some-file.zip"   | "zip"     | "1.2"
        ".zip"              | ""                | "zip"     | "1.2"
        "some-file"         | "some-file"       | ""        | "1.2"

        "some-file.zip"     | "some-file"       | "zip"     | null
        "some-file.zip.zip" | "some-file.zip"   | "zip"     | null
        ".zip"              | ""                | "zip"     | null
        "some-file"         | "some-file"       | ""        | null
    }

    def "removes module version from file name #inputFileName with version #version"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), version)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == null

        where:
        inputFileName            | name                 | extension | version
        "some-file-1.2.zip"      | "some-file"          | "zip"     | "1.2"
        "some-file-1.2-1.2.zip"  | "some-file-1.2"      | "zip"     | "1.2"
        "some-file-1.2"          | "some-file"          | ""        | "1.2"
        "some-file-1.22.zip"     | "some-file-1.22"     | "zip"     | "1.2"
        "some-file-1.22.zip.zip" | "some-file-1.22.zip" | "zip"     | "1.2"

        "some-file-1.2.zip"      | "some-file-1.2"      | "zip"     | null
        "some-file-1.2-1.2.zip"  | "some-file-1.2-1.2"  | "zip"     | null
        "some-file-1.2"          | "some-file-1"        | "2"       | null
        "some-file-1.22.zip"     | "some-file-1.22"     | "zip"     | null
        "some-file-1.22.zip.zip" | "some-file-1.22.zip" | "zip"     | null
    }

    def "determines classifier from file name #inputFileName with version #version"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), version)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == classifier

        where:
        inputFileName                      | name                           | classifier    | extension         | version
        "some-file-1.2-classifier.jar"     | "some-file"                    | "classifier"  | "jar"             | "1.2"
        "some-file-1.2-classifier-1.2.jar" | "some-file-1.2-classifier"     | null          | "jar"             | "1.2"
        "-1.2-classifier.jar"              | ""                             | "classifier"  | "jar"             | "1.2"
        "some-file-1.2-classifier"         | "some-file"                    | "classifier"  | ""                | "1.2"
        "some-file-1.2-.jar"               | "some-file"                    | null          | "jar"             | "1.2"

        "some-file-1.2-classifier.jar"     | "some-file-1.2-classifier"     | null          | "jar"             | null
        "some-file-1.2-classifier-1.2.jar" | "some-file-1.2-classifier-1.2" | null          | "jar"             | null
        "-1.2-classifier.jar"              | "-1.2-classifier"              | null          | "jar"             | null
        "some-file-1.2-classifier"         | "some-file-1"                  | null          | "2-classifier"    | null
        "some-file-1.2-.jar"               | "some-file-1.2-"               | null          | "jar"             | null
    }
}
