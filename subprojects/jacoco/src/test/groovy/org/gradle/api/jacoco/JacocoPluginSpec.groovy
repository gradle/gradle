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
package org.gradle.api.jacoco

import spock.lang.Specification

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder

class JacocoPluginSpec extends Specification {
	Project project = ProjectBuilder.builder().withProjectDir(new File(System.properties['java.io.tmpdir'])).build()

	def setup() {
		project.apply plugin: 'jacoco'
	}

	def 'jacoco applied to specific JavaExec task'() {
		given:
		JavaExec task = project.tasks.add('exec', JavaExec)
		when:
		project.jacoco.applyTo(task)
		then:
		task.extensions.getByType(JacocoTaskExtension) != null
	}

	def 'jacoco applied to Test task'() {
		given:
		Test task = project.tasks.add('test', Test)
		expect:
		task.extensions.getByType(JacocoTaskExtension) != null
	}
}
