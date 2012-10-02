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

package org.gradle.integtests.resolve.custom

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.util.TextUtil

class FileSystemResolverIntegrationTest extends AbstractIntegrationSpec {

    def "file system resolvers use item at source by default"() {
        when:
        def repoDir = testDir.createDir("repo")
        def repo = new IvyRepository(repoDir)
        def repoDirPath = TextUtil.escapeString(repoDir.absolutePath)
        def module = repo.module("group", "projectA", "1.2")
        module.publish()
        def jar = module.jarFile
        jar.text = "1"
        
        buildFile << """
            repositories {
                add(new org.apache.ivy.plugins.resolver.FileSystemResolver()) {
                    name = "repo"
                    addIvyPattern("$repoDirPath/[organization]/[module]/[revision]/ivy-[module]-[revision].xml")
                    addArtifactPattern("$repoDirPath/[organization]/[module]/[revision]/[module]-[revision].[ext]")
                }
            }
            configurations { compile }
            dependencies { compile 'group:projectA:1.2' }
            task echoContent << {
                def dep = configurations.compile.singleFile
                println "content: " + dep.text
                println "path: " + dep.canonicalPath
            }
        """

        then:
        succeeds 'echoContent'
        scrapeValue("content") == "1"
        scrapeValue("path") == jar.canonicalPath

        when:
        jar.text = "2"

        then:
        succeeds 'echoContent'
        scrapeValue("content") == "2"
        scrapeValue("path") == jar.canonicalPath
    }

    protected scrapeValue(label) {
        def fullLabel = "$label: "
        for (line in output.readLines()) {
            if (line.startsWith(fullLabel))  {
                return line - fullLabel
            }
        }

        null
    }
}
