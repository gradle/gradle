/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.archive

import org.hamcrest.Matcher

import java.util.zip.ZipFile

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.hasItems

class ZipTestFixture {

    Map fileContents = [:]

    ZipTestFixture(File file) {
        ZipFile zipFile = null
        try {
            zipFile = new ZipFile(file)
            def entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                def entry = entries.nextElement()
                def content = zipFile.getInputStream(entry).text
                if (!entry.directory) {
                    fileContents[entry.name] = fileContents[entry.name] ? fileContents[entry.name] + content : [content]
                }
            }
        } finally {
            if (zipFile) {
                zipFile.close();
            }
        }
    }

    def containsOnly(String... fileNames) {
        assertThat(fileNames as List, hasItems(fileContents.keySet().toArray()))
        this
    }

    String assertFileContent(String filepath, String fileContent) {
        assertThat(fileContents.keySet(), hasItem(filepath))
        assertThat(fileContents[filepath], hasItem(fileContent))
        this
    }

    def assertFileContent(String filePath, Matcher matcher) {
        assertThat(fileContents.keySet(), hasItem(filePath))
        assertThat(fileContents[filePath], matcher)
        this
    }

    Integer countFiles(String filePath) {
        fileContents.findAll({ it == filePath }).size()
    }
}
