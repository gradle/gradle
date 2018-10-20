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

import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import groovy.ui.SystemOutputInterceptor

class CppMD5ErrorPreventionIntegrationTest extends AbstractInstalledToolChainIntegrationSpec  implements CppTaskNames {
	
	private static final String APP = 'md5'

    TestFile mainSourceFile
    TestFile mainHeaderFile
	String sourceType = "cpp"
	
	def out = installation("build/install/main/debug")
	def projectTasks = tasks(APP)
 
	def setup() {
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
		settingsFile << """
            rootProject.name = 'test'
            include 'library', 'app'
        """

		mainSourceFile = file("list/main.cpp") << """
			#include EXTERNAL_HEADER
			#include <iostream>
			
			int main() {
				std::cout << SAY_SOMETHING << std::endl;
				return 0;
			}
			
        """

		mainHeaderFile = file("list/main.h") << """
			#pragma once
			#define SAY_SOMETHING "Hello World!!"
        """
	}

	
    def "can prevent md5 error when snapshotshotting source dir includes root dir"() {
	
		for(String s: projectTasks) {
			System.out.println ("########  TASKS  #################")
			System.out.println (s)
		}

		given:
		run 'build'

		expect:
		for(String s: result.getExecutedTasks()) {
			System.out.println ("###########  EXECUTED TASKS  ##################")
			System.out.println (s)
		}
		for (TestFile tf :out.getLibraryFiles() ) {
			System.out.println ("###########  LIBRARY FILES  ##################")
			System.out.println(tf.md5())
		}

    }
}
