/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * Adds the ability to digitially sign files and artifacts.
 */
class SigningPlugin implements Plugin<Project> {

    /**
     * <p>Adds the ability to digitially sign files and artifacts.</p>
     * 
     * <p>Attaches a {@link org.gradle.plugins.signing.SigningPluginConvention signing convention} with the name “signing”, connected to a
     * {@link org.gradle.plugins.signing.SigningSettings signing settings} for this project.</p>
     * 
     * <p>Also adds conventions to all {@link org.gradle.plugins.signing.Sign sign tasks} to use the signing setting defaults.</p>
     * 
     * @see org.gradle.plugins.signing.SigningPluginConvention
     * @see org.gradle.plugins.signing.SigningSettings#addSignatureSpecConventions(SigningSpec)
     */
    void apply(Project project) {
        def settings = new SigningSettings(project)
        def convention = new SigningPluginConvention(settings)
        project.convention.plugins.signing = convention
        
        project.tasks.withType(Sign) { task ->
            project.settings.addSignatureSpecConventions(task)
        }
    }
    
}