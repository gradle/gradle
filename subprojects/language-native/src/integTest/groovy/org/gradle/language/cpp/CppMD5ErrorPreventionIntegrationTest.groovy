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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.AppElement
import org.gradle.nativeplatform.fixtures.app.SourceFileElement
import org.gradle.test.fixtures.file.TestFile

class CppMD5ErrorPreventionIntegrationTest extends AbstractInstalledToolChainIntegrationSpec  implements CppTaskNames {

	private static final String APP = 'md5'

	TestFile mainSourceFile
	TestFile mainHeaderFile
	String sourceType = "cpp"

	def out = installation("build/install/main/debug")
	def projectTasks = tasks(APP)

	def "compile source and header"() {
		given:
		settingsFile << "rootProject.name='md5'"
		
		and:
        def app  = new CppSimpleMain()
		app.writeToProject(testDirectory)

		and:
		mainHeaderFile = file("list/main.h") << """
			#pragma once
			#define SAY_SOMETHING "Hello World!!"
        """

		and:
		buildFile << """
			plugins {
			    id 'cpp-application'
			}
			
			application {
			    source.from project.fileTree(dir: '.', include: '**/*.cpp')
			    privateHeaders.from project.file('.')
			}
			
			tasks.withType(CppCompile) {
			    macros.put('EXTERNAL_HEADER', '<list/main.h>')
			}
		"""

		expect:
		succeeds "build"
		//result.assertTasksExecuted(compileTasks(debug), ":build")
	}
}

class CppSimpleMain extends SourceFileElement implements AppElement {

	final SourceFile sourceFile = sourceFile("list", "main.cpp", """
			#include EXTERNAL_HEADER
			#include <iostream>
			
			int main() {
				std::cout << SAY_SOMETHING << std::endl;
				return 0;
			}
    """
	)
	
	@Override
	String getExpectedOutput() {
		return "Hello World!!"
	}

}

