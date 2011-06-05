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
package org.gradle.plugin.pgp.signing

import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection

import org.gradle.api.Project
import org.gradle.api.InvalidUserDataException

class SignatoryFactory {
	
	Signatory createSignatory(Project project) {
		["pgpKeyId", "pgpSecretKeyRingFile", "pgpPassword"].each {
			if (!project.hasProperty(it)) {
				throw new InvalidUserDataException("'$it' property could not be found on project and is needed for signing")
			}
		}
		
		createSignatory(project.pgpKeyId, project.file(project.pgpSecretKeyRingFile), project.pgpPassword)
	}
	
	
	Signatory createSignatory(String keyId, File keyRing, String password) {
		createSignatory(readSecretKey(keyId, keyRing), password)
	}
	
	Signatory createSignatory(PGPSecretKey secretKey, String password) {
		new Signatory(secretKey, password)
	}
		
	PGPSecretKey readSecretKey(String keyId, File file) {
		file.withInputStream { readSecretKey(it, keyId, "file: $file.absolutePath") }
	}
	
	protected PGPSecretKey readSecretKey(InputStream input, String keyId, String sourceDescription) {
		readSecretKey(new PGPSecretKeyRingCollection(input), normalizeKeyId(keyId), sourceDescription)
	}
	
	protected PGPSecretKey readSecretKey(PGPSecretKeyRingCollection keyRings, KeyId keyId, String sourceDescription) {
		def key = keyRings.keyRings.find { new KeyId(it.secretKey.keyID) == keyId }?.secretKey
		if (key == null) {
			throw new InvalidUserDataException("did not find secret key for id '$keyId' in key source '$sourceDescription'")
		}
		key
	}
	
	// TODO - move out to DSL adapter layer (i.e. signatories container)
	protected KeyId normalizeKeyId(String keyId) {
		try {
			new KeyId(keyId)
		} catch (IllegalArgumentException e) {
			throw new InvalidUserDataException(e.message)
		}
	}
}