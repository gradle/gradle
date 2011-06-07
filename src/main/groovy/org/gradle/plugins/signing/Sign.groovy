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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

import org.gradle.plugins.signing.signatory.Signatory

/**
 * Creates a file containing a digital signature alongside an input file.
 */
class Sign extends DefaultTask {
	
	private final SignOperation operation
	final SigningSettings settings
	
	Sign() {
		super()
		settings = project.signing
		operation = new SignOperation(settings)
	}

	void sign(AbstractArchiveTask task) {
		dependsOn(task)
		sign(task.archivePath, task.classifier)
	}
	
	void sign(AbstractArchiveTask task, SignatureType type) {
		dependsOn(task)
		sign(task.archivePath, type, task.classifier)
	}
	
	void sign(PublishArtifact artifact) {
		dependsOn(artifact.buildDependencies)
		operation.sign(artifact, this)
	}
	
	void sign(PublishArtifact artifact, SignatureType type) {
		dependsOn(artifact.buildDependencies)
		operation.sign(artifact, type, this)
	}
	
	void sign(File toSign, String classifier = null) {
		operation.sign(toSign, classifier, this)
	}
	
	void sign(File toSign, SignatureType type, String classifier = null) {
		operation.sign(toSign, type, classifier, this)
	}

	void signatory(Signatory signatory) {
		operation.signatory(signatory)
	}
	
	@TaskAction
	void doSigning() {
		operation.execute()
	}
	
	@InputFile
	File getToSign() {
		operation.toSign
	}

	Signatory getSignatory() {
		operation.signatory
	}

	SignatureType getType() {
		operation.type
	}
	
	@OutputFile 
	File getSignature() {
		operation.signature
	}
	
	PublishArtifact getArtifact() {
		operation.artifact
	}
}