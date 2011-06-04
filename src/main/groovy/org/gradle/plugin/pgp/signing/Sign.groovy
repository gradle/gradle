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

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection

/**
 * A task for signing the build artifacts of a configuration, adding the generated signature files
 * as artifacts to another (potentially the same) configuration.
 */
class Sign extends DefaultTask {
	
	Configuration from
	Configuration addTo
	String password
	PGPSecretKey secretKey
	
	void secretKeyFile(String keyId, String path) {
		secretKeyFile(keyId, project.file(path))
	}
	
	void secretKeyFile(String keyId, File file) {
		secretKey = file.withInputStream { readSecretKey(it, keyId, "$file.absolutePath") }
	}
	
	void password(String password) {
		this.password = password
	}
	
	void from(Configuration configuration) {
		setFrom(configuration)
	}
	
	void setFrom(Configuration configuration) {
		if (from != null) {
			throw new IllegalStateException("Cannot change 'from' after it has been set")
		}
		this.from = configuration
		dependsOn(configuration.buildArtifacts)
	}
	
	void addTo(Configuration configuration) {
		this.addTo = addTo
	}
	
	protected PGPSecretKey readSecretKey(InputStream input, String keyId, String sourceDescription) {
		readSecretKey(new PGPSecretKeyRingCollection(input), normalizeKeyId(keyId), sourceDescription)
	}
	
	protected PGPSecretKey readSecretKey(PGPSecretKeyRingCollection keyRings, PgpKeyId keyId, String sourceDescription) {
		def key = keyRings.keyRings.find { new PgpKeyId(it.secretKey.keyID) == keyId }?.secretKey
		if (key == null) {
			throw new InvalidUserDataException("did not find secret key for id '$keyId' in key source '$sourceDescription'")
		}
		key
	}
	
	protected PgpKeyId normalizeKeyId(String keyId) {
		try {
			new PgpKeyId(keyId)
		} catch (IllegalArgumentException e) {
			throw new InvalidUserDataException(e.message)
		}
	}
	
	@TaskAction
	void sign() {
		def signer = createSigner()
		from.allArtifacts.each { PublishArtifact artifact ->
			signer.sign(artifact.file, PgpSigner.OutputType.all).each { outputType, file ->
				addTo.addArtifact(createArtifactForSignature(file, outputType))
			}
		}
	}
	
	protected createSigner() {
		new PgpSigner(secretKey, password)
	}
	
	protected PublishArtifact createArtifactForSignature(File signatureFile, PgpSigner.OutputType outputType) {
		new DefaultPublishArtifact(
			signatureFile.name,
			outputType.fileExtension,
			outputType.fileExtension,
			null, // no classifier
			null, // no specific date, use now
			signatureFile,
			this
		)
	}
	
}