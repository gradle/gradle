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

import org.bouncycastle.bcpg.ArmoredOutputStream

import org.gradle.plugins.signing.signatory.Signatory

enum SignatureType {
	BINARY("sig"),
	ARMORED("asc", { new ArmoredOutputStream(it) })

	final String fileExtension
	private Closure outputDecorator
	
	SignatureType(String fileExtension, Closure outputDecorator = { it }) {
		this.fileExtension = fileExtension
		this.outputDecorator = outputDecorator
	}

	void sign(Signatory signatory, InputStream toSign, OutputStream destination) {
		signatory.sign(toSign, outputDecorator(destination))
	}
		
	File fileFor(File toSign) {
		new File(toSign.absolutePath + ".$fileExtension")
	}
	
	String combinedExtension(File toSign) {
		def name = toSign.name
		def dotIndex = name.lastIndexOf(".")
		if (dotIndex == -1 || dotIndex + 1 == name.size()) {
			fileExtension
		} else {
			name[++dotIndex..-1] + ".$fileExtension"
		}
	}
	
	File sign(Signatory signatory, File toSign) {
		def signatureFile = fileFor(toSign)
		toSign.withInputStream { toSignStream ->
			signatureFile.withOutputStream { signatureFileStream ->
				sign(signatory, toSignStream, signatureFileStream)
			}
		}
		signatureFile
	}
	
}
