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

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

import org.gradle.util.ConfigureUtil

import org.gradle.plugins.signing.signatory.Signatory

class SignOperation {

	private File toSign
	private Signatory signatory
	private SignatureType type
	
	private File signature
	private PublishArtifact artifact
	
	private final SigningSettings settings
	
	SignOperation(SigningSettings settings) {
		this.settings = settings
	}
	
	SignOperation sign(PublishArtifact artifact, Object[] tasks) {
		sign(artifact.file, artifact.classifier, tasks)
	}

	SignOperation sign(PublishArtifact artifact, SignatureType type, Object[] tasks) {
		sign(artifact.file, type, artifact.classifier, tasks)
	}
	
	SignOperation sign(File toSign, String classifier = null, Object[] tasks) {
		sign(toSign, SignatureType.ARMORED, classifier, tasks)
	}
	
	SignOperation sign(File toSign, SignatureType type, String classifier = null, Object[] tasks) {
		this.toSign = toSign
		this.type = type
		this.signature = type.fileFor(toSign)
		this.artifact = new DefaultPublishArtifact(
			signature.name,
			"Signature ($type.fileExtension)",
			type.combinedExtension(toSign),
			classifier,
			null, // no specific date, use now
			signature,
			tasks == null ? [] : tasks
		)
		this
	}

	SignOperation signatory(Signatory signatory) {
		this.signatory = signatory
		this
	}
	
	SignOperation execute() {
		// TODO - explode if no signatory has been defined
		type.sign(getSignatory(), toSign)
		this
	}
	
	File getToSign() {
		toSign
	}

	Signatory getSignatory() {
		signatory ?: settings.defaultSignatory
	}

	SignatureType getType() {
		type
	}
	
	File getSignature() {
		signature
	}
	
	PublishArtifact getArtifact() {
		artifact
	}
	
	SignOperation configure(Closure closure) {
		if (closure) {
			ConfigureUtil.configure(block, this)
		}
		execute()
	}
}
