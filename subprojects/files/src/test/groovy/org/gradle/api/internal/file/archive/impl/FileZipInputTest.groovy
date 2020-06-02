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

package org.gradle.api.internal.file.archive.impl

import org.gradle.api.JavaVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.zip.ZipOutputStream


class FileZipInputTest extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def setup() {
        temporaryFolder.create()
    }

    def "selects the correct zip input type"() {
        def file = makeZip("foo.zip")

        when:
        def zipInput = FileZipInput.create(file)

        then:
        if (JavaVersion.current().java11Compatible) {
            assert zipInput instanceof FileZipInput
        } else {
            assert zipInput instanceof StreamZipInput
        }
    }

    private File makeZip(String filename) {
        def file = temporaryFolder.newFile(filename)
        new ZipOutputStream(new FileOutputStream(file)).close()
        return file
    }
}
