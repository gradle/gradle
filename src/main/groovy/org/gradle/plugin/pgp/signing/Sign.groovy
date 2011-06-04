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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

/**
 * Creates a file containing a digital signature alongside an input file.
 */
class Sign extends DefaultTask {
	
	private File toSign
	private Signatory signatory
	private SignatureType type
	
	private File signature
	private PublishArtifact artifact

	void sign(AbstractArchiveTask task, SignatureType type) {
		dependsOn(task)
		sign(task.archivePath, type)
	}
	
	void sign(File toSign, SignatureType type) {
		this.toSign = toSign
		this.type = type
		this.signature = type.fileFor(toSign)
		this.artifact = new DefaultPublishArtifact(
			signature.name,
			type.fileExtension,
			type.fileExtension,
			null, // no classifier
			null, // no specific date, use now
			signature,
			this
		)
	}

	void signatory(Signatory signatory) {
		this.signatory = signatory
	}
	
	@TaskAction
	void doSigning() {
		type.sign(signatory, toSign)
	}
	
	@InputFile
	File getToSign() {
		toSign
	}

	Signatory getSignatory() {
		signatory
	}

	SignatureType getType() {
		type
	}
	
	@OutputFile 
	File getSignature() {
		signature
	}
	
	PublishArtifact getArtifact() {
		artifact
	}
}