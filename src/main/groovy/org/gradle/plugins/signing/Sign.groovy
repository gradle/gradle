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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.file.FileCollection

import org.gradle.plugins.signing.signatory.Signatory

/**
 * Creates a file containing a digital signature alongside an input file.
 */
class Sign extends DefaultTask {
	
	final SigningSettings settings
	final private SignOperation operation
	
	Sign() {
		super()
		settings = project.signing
		operation = new SignOperation(settings)
	}
	
	void sign(AbstractArchiveTask... toSign) {
		for (it in toSign) {
			dependsOn(it)
			addSignature(it, it.archivePath, it.classifier)
		}
	}
	
	void sign(PublishArtifact... toSign) {
		for (it in toSign) {
			dependsOn(it.buildDependencies)
			addSignature(it, it.file, it.classifier)
		}
	}
	
	void sign(File... toSign) {
		sign(null, *toSign)
	}
	
	void sign(String classifier, File... toSign) {
		for (it in toSign) {
			addSignature(it, it, classifier)
		}
	}
	
	private addSignature(Object source, File toSign, String classifier = null) {
		def signature = operation.addSignature(source, toSign, classifier, this)
		
		// Not using @InputFiles because there is no @OutputFiles, better to be consistent
/*		inputs.file(toSign)*/
/*		outputs.file(signature.file)*/
	}
	
	void signatory(Signatory signatory) {
		operation.signatory(signatory)
	}
		
	Signatory getSignatory() {
		operation.signatory
	}

	void type(SignatureType type) {
		operation.type(type)
	}

	SignatureType getType() {
		operation.type
	}
	
	@InputFile
	FileCollection getSigned() {
		operation.signed
	}
	
	@OutputFile
	FileCollection getFiles() {
		operation.files
	}

	PublishArtifact[] getArtifacts() {
		operation.artifacts
	}

	PublishArtifact getSingleArtifact() {
		operation.singleArtifact
	}
	
	Signature getSingleSignature() {
		operation.singleSignature
	}
	
	@TaskAction
	void execute() {
		operation.execute()
	}
}