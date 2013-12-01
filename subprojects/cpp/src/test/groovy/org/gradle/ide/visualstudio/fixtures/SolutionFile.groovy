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

package org.gradle.ide.visualstudio.fixtures

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

class SolutionFile {
    String content
    Map<String, Project> projects = [:]

    SolutionFile(TestFile solutionFile) {
        assert solutionFile.exists()
        content = TextUtil.normaliseLineSeparators(solutionFile.text)

        content.findAll(~/(?m)^Project\(\"\{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942\}\"\) = \"(\w+)\", \"([^\"]*)\", \"\{([\w\-]+)\}\"$/, {
            projects.put(it[1], new Project(it[1], it[2], it[3]))
        })
    }

    def assertHasProjects(String... names) {
        assert projects.keySet() == names as Set
        return true
    }

    class Project {
        final String name
        final String file
        final String rawUuid

        Project(String name, String file, String rawUuid) {
            this.name = name
            this.file = file
            this.rawUuid = rawUuid
        }

        String getUuid() {
            return '{' + rawUuid + '}'
        }

        List<String> getConfigurations() {
            content.findAll(~/\{${rawUuid}\}\.(\w+\|\w+)\.ActiveCfg = debug\|Win32/, {
                it[1]
            })
        }
    }
}
