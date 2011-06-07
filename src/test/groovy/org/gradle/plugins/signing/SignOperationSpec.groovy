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

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

class SignOperationSpec extends SigningProjectSpec {
	
	def input
	def output
	
	def setup() {
		applyPlugin()
		addSigningProperties()
		input = getResourceFile("some.txt")
		output = signing.type.fileFor(input)
		assert !output.exists() || output.delete()
		assert input.text // don't care what it is, just need some
	}
	
	def "sign file with defaults"() {
		when:
		def operation = sign(input)
		
		then:
		output.exists()
		output == operation.signature
		output == operation.artifact.file
	}
	
	def "sign artifact with defaults"() {
		given:
		def inputArtifact = new DefaultPublishArtifact(input.name, "Text File", "txt", null, null, input)
		
		when:
		def operation = sign(inputArtifact)
		
		then:
		output.exists()
		output == operation.signature
		output == operation.artifact.file
	}
	
}