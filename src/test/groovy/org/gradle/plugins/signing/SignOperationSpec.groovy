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

class SignOperationSpec extends SigningProjectSpec {
	
	def setup() {
		applyPlugin()
		addSigningProperties()
	}
	
	def "sign with defaults"() {
		given:
		def input = getResourceFile("some.txt")
		def output = signing.type.fileFor(input)
		
		expect:
		input.text // don't care what it is, just need some
		!output.exists() || output.delete()
		
		when:
		def operation = sign(input)
		
		then:
		operation.signature.exists()
	}
	
}