/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildSrcLocationIntegrationTest extends AbstractIntegrationSpec {

    def "buildSrc directory is always relative to settings dir"() {
        when:
        file("buildSrc/src/main/groovy/Thing.groovy") << "class Thing {}"
        settingsFile << """
            rootProject.projectDir = new File(rootDir, 'root')
        """

        def movedBuildScript = file("root/build.gradle") << ""

        args('help', "-b", movedBuildScript.absolutePath, "-c", settingsFile.absolutePath)

        succeeds("help")

        then:
        executed(":buildSrc:compileGroovy")
    }

}
