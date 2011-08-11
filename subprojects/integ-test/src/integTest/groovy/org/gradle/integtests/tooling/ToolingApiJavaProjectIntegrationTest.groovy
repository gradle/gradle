/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiJavaProjectIntegrationTest extends ToolingApiSpecification {
    def projectDir = dist.testDir
    def repoDir = projectDir.file("repo")

    def "can get model for project with external dependency"() {
        projectDir.file("src/main/java/org/myorg/MyClass2.java") << """
package org.myorg;

public class MyClass2 extends MyClass1 {

}
        """

        projectDir.file("src/test/java/org/myorg/MyTest2.java") << """
package org.myorg;

public class MyTest2 extends MyTest1 {

}
        """

        projectDir.file("build.gradle") << """
        apply plugin: 'java'
apply plugin: 'maven'

group = "org.myorg"
version = "0.1"

repositories {
	flatDir(name: 'fileRepo', dirs: "/swd/tmp/Testprojects_flatDirRepo/repo") //use a shared flat dir repo
}


uploadArchives {
	repositories {
		add project.repositories.fileRepo
	}
}

//generates tests jar
task packageTests(type: Jar, dependsOn: 'testClasses') {
	from sourceSets.test.classes
	classifier = 'tests'
}

artifacts {
	/*
	 * adds test jars to archives to be uploaded
	 */
	archives (packageTests)
}


dependencies {
	compile 'org.myorg:Testproject1:0.1'

	testCompile 'org.myorg:Testproject1:0.1:tests'
}
        """

        when:
        EclipseProject project = toolingApi.withConnection { connection ->
            connection.getModel(EclipseProject.class)
        }

        then:
        project != null
    }
}
