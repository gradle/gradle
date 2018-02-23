/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.cleanup

import org.gradle.api.Project
import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class EmptyDirectoryCheckTest extends Specification {
    @Rule
    public final TemporaryFolder tempProjectDir = new TemporaryFolder()

    Project project
    File buildFile
    File targetDir
    File leftoverReport

    def setup() {
        buildFile = tempProjectDir.newFile('build.gradle')
        targetDir = tempProjectDir.newFolder("clean_me")
        tempProjectDir.newFolder("clean_me", "casserole")
        leftoverReport = new File(tempProjectDir.getRoot(), "reports/leftovers.txt")
    }

    @Unroll
    def "empty directory creates no output. errors: #withErrors"() {
        given:
        buildFile(withErrors)

        when:
        leftoversTask().execute()

        then:
        !leftoverReport.exists()

        where:
        withErrors << [true, false]
    }

    def "reports existing files"() {
        given:
        def files = populateLeftovers()
        buildFile(false /* withErrors */)

        when:
        leftoversTask().execute()

        then:
        leftoverReport.exists()
        def output = leftoverReport.getText("UTF-8")
        files.each {
            output.contains(it.path)
        }
    }

    def "reports existing files and errors"() {
        given:
        def files = populateLeftovers()
        buildFile(true /* withErrors */)

        when:
        leftoversTask().execute()

        then:
        TaskExecutionException tee = thrown()
        tee.getCause().getMessage().contains(targetDir.canonicalPath)
        leftoverReport.exists()
        def output = leftoverReport.getText("UTF-8")
        files.each {
            output.contains(it.path)
        }
    }

    private def buildFile(boolean withErrors) {
        buildFile << """
            import org.gradle.cleanup.EmptyDirectoryCheck

            task leftovers(type: EmptyDirectoryCheck) {
                targetDir = fileTree('clean_me')
                report = file('reports/leftovers.txt')
                errorWhenNotEmpty = $withErrors
            }
        """
    }

    private def populateLeftovers() {
        def files = new ArrayList<File>();
        files.add(tempProjectDir.newFile("clean_me/casserole/peas.txt"))
        files.add(tempProjectDir.newFile("clean_me/casserole/tuna.txt"))
        files.add(tempProjectDir.newFile("clean_me/meatloaf.txt"))
        return files
    }

    private def leftoversTask() {
        project = ProjectBuilder.builder().withProjectDir(tempProjectDir.getRoot()).build()
        return (AbstractTask) project.getTasksByName("leftovers", false /* recursive */).first()
    }
}
