/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.distribution.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.distribution.DistributionContainer;

/**
 * <p>Applies the {@link DistributionBasePlugin} and adds a conventional {@link #MAIN_DISTRIBUTION_NAME main} distribution.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/distribution_plugin.html">Distribution plugin reference</a>
 */
public abstract class DistributionPlugin implements Plugin<Project> {

    /**
     * Name of the main distribution
     */
    public static final String MAIN_DISTRIBUTION_NAME = "main";

    /**
     * The name of the install task for the main distribution.
     */
    public static final String TASK_INSTALL_NAME = "installDist";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(DistributionBasePlugin.class);

        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        distributions.create(MAIN_DISTRIBUTION_NAME);
    }

}
