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

package org.gradle.ide.xcode.fixtures

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

class WorkspaceFile {
    final TestFile file
    final String name
    final Node contentXml

    WorkspaceFile(TestFile workspaceFile) {
        workspaceFile.assertIsFile()
        file = workspaceFile
        name = file.name.replace(".xcworkspacedata", "")
        contentXml = new XmlParser().parse(file)
    }

    void assertHasProjects(String... paths) {
        assert contentXml.FileRef.size() == paths.length
        paths.each { path ->
            assertHasProject(file.parentFile.parentFile.file(path))
        }
    }

    void assertHasProject(File projectFile) {
        assert contentXml.FileRef*.@location*.replaceAll('absolute:', '').contains(projectFile.absolutePath)
    }
}
