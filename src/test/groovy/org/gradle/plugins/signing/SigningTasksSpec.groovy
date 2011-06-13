/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.signing

class SigningTasksSpec extends SigningProjectSpec {
	
	def setup() {
		applyPlugin()
	}
		
	def "sign jar with defaults"() {
		given:
		useJavadocAndSourceJars()
		
		when:
		signing {
			sign jar
			sign sourcesJar, javadocJar
		}
		
		then:
		def signJarTask = tasks["jar-sign"]
		def signSourcesJarTask = tasks["sourcesJar-sign"]
		def signJavadocJarTask = tasks["javadocJar-sign"]
		def signingTasks = [signJarTask, signSourcesJarTask, signJavadocJarTask]
		
		and:
		jar in signJarTask.dependsOn
		sourcesJar in signSourcesJarTask.dependsOn
		javadocJar in signJavadocJarTask.dependsOn
		
		and:
		signingTasks.every { it.singleArtifact in configurations.signatures.artifacts }

		and:
		signingTasks.every { it.signatory == signing.defaultSignatory }
	}
	
	def "sign method return values"() {
		given:
		useJavadocAndSourceJars()
		
		when:
		def signJarTask = signing.sign(jar)
		
		then:
		signJarTask.name == "jar-sign"
		
		when:
		def (signSourcesJarTask, signJavadocJarTask) = signing.sign(sourcesJar, javadocJar)
		
		then:
		[signSourcesJarTask, signJavadocJarTask]*.name == ["sourcesJar-sign", "javadocJar-sign"]
	}
	
}