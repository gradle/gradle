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

package org.gradle.plugins.signing;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;

/**
 * Adds the ability to digitally sign files and artifacts.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/signing_plugin.html">Signing plugin reference</a>
 */
public abstract class SigningPlugin implements Plugin<Project> {

    /**
     * <p>Adds the ability to digitally sign files and artifacts.</p>
     *
     * <p>Adds the extension {@link SigningExtension} with the name "signing".</p>
     * <p>Also adds conventions to all {@link Sign sign tasks} to use the signing extension setting defaults.</p>
     *
     * @see SigningExtension
     */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getExtensions().create("signing", SigningExtension.class, project);
    }
}
