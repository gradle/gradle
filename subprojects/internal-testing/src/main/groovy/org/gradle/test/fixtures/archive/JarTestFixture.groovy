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

import java.util.jar.JarFile

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItem


class JarTestFixture {
    List allFiles= []
    List services = []
    List allNonServiceTextContent = []

    JarTestFixture(File file) {
        JarFile jarFile = null
        try{
            jarFile = new JarFile(file)
            def entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                def entry = entries.nextElement()
                allFiles += entry.name
                def lines = jarFile.getInputStream(entry).readLines()
                if (entry.name.endsWith('org.gradle.Service')) {
                    services.addAll(lines)
                } else {
                    allNonServiceTextContent.addAll(lines)
                }
            }
        } finally {
            if(jarFile){
                jarFile.close();
            }
        }
    }

    int countFiles(String filePath) {
        allFiles.findAll({ it == filePath}).size()
    }

    def assertTextFileContent(Matcher matcher) {
        assertThat(allNonServiceTextContent, matcher)
        this
    }

    def hasService(String serviceName) {
        assertThat(services, hasItem(equalTo(serviceName)))
        this
    }
}
