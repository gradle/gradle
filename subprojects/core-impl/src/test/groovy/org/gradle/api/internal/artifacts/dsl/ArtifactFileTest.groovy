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
public class ArtifactFileTest extends Specification {
    final String module = '1.2'

    def "determines name and extension from file name"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), module)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == null

        where:
        inputFileName       | name              | extension
        "some-file.zip"     | "some-file"       | "zip"
        "some-file.zip.zip" | "some-file.zip"   | "zip"
        ".zip"              | ""                | "zip"
        "some-file"         | "some-file"       | null
    }

    def "removes module version from file name"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), module)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == null

        where:
        inputFileName            | name                 | extension
        "some-file-1.2.zip"      | "some-file"          | "zip"
        "some-file-1.2-1.2.zip"  | "some-file-1.2"      | "zip"
        "some-file-1.2"          | "some-file"          | null
        "some-file-1.22.zip"     | "some-file-1.22"     | "zip"
        "some-file-1.22.zip.zip" | "some-file-1.22.zip" | "zip"
    }
    
    def "determines classifier from file name"() {
        when:
        def artifactFile = new ArtifactFile(new File(inputFileName), module)

        then:
        artifactFile.name == name
        artifactFile.extension == extension
        artifactFile.classifier == classifier

        where:
        inputFileName                      | name                       | classifier   | extension
        "some-file-1.2-classifier.jar"     | "some-file"                | "classifier" | "jar"
        "some-file-1.2-classifier-1.2.jar" | "some-file-1.2-classifier" | null         | "jar"
        "-1.2-classifier.jar"              | ""                         | "classifier" | "jar"
        "some-file-1.2-classifier"         | "some-file"                | "classifier" | null
        "some-file-1.2-.jar"               | "some-file"                | null         | "jar"
    }
}
