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

import org.gradle.api.Task
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.util.ConfigureUtil

import org.gradle.plugins.signing.signatory.*

class SigningSettings {
	
	static final String DEFAULT_CONFIGURATION_NAME = "signatures"
	
	private Project project
	private Map<String, Signatory> signatories = [:]
	private Configuration configuration
	
	SigningSettings(Project project) {
		this.project = project
		this.configuration = getDefaultConfiguration()
	}
	
	protected Configuration getDefaultConfiguration() {
		def configurations = project.configurations
		def configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME)
		if (configuration == null) {
			configuration = configurations.add(DEFAULT_CONFIGURATION_NAME)
		}
		configuration
	}
	
	Map<String, Signatory> signatories(Closure block) {
		ConfigureUtil.configure(block, new SignatoriesConfigurer(this))
		signatories
	}
	
	Signatory getDefaultSignatory() {
		new SignatoryFactory().createSignatory(project)
	}
	
	Configuration getConfiguration() {
		configuration
	}
	
	Sign sign(Task task) {
		sign([task] as Task[]).first()
	}
	
	Collection<Sign> sign(Task[] tasksToSign) {
		tasksToSign.collect { taskToSign ->
			def signTask = project.task("${taskToSign.name}-sign", type: Sign) {
				sign taskToSign
			}
			configuration.addArtifact(signTask.artifact)
			signTask
		}
	}
	
	Sign sign(PublishArtifact artifact) {
		def signTask = project.task("${artifact.name}-sign", type: Sign) {
			delegate.sign artifact
		}
		configuration.addArtifact(signTask.artifact)
		signTask
	}
	
}