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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.gradle.api.NamedDomainObjectContainer

import org.gradle.plugins.signing.signatory.*

class SigningPlugin implements Plugin<Project> {
	
	void apply(Project project) {
		def projectConfig = new SigningConfiguration(project)
		def signingConvention = new SigningConvention(projectConfig)
		project.convention.plugins.signing = signingConvention
	}
	
	/**
	 * The top level interface mixed in to the project
	 */
	static class SigningConvention {
		private SigningConfiguration projectConfig
		
		SigningConvention(SigningConfiguration projectConfig) {
			this.projectConfig = projectConfig
		}
		
		SigningConvention signing(Closure block) {
			ConfigureUtil.configure(block, projectConfig)
			this
		}
		
		Map<String, Signatory> getSignatories() {
			projectConfig.signatories
		}
		
		Signatory getDefaultSignatory() {
			projectConfig.defaultSignatory
		}

		SigningConfiguration getSigningConfiguration() {
			projectConfig
		}
	}
	
}