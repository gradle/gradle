/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.plugins;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.util.ConfigureUtil;

/**
 * Configuration options for the JavaGradlePlugin plugin.
 *
 * <p>Below is a full configuration example. Since all properties have sensible defaults,
 * typically only selected properties will be configured.
 *
 * <pre autoTested=''>
 *     apply plugin: "java-gradle-plugin"
 *
 *     javaGradlePlugin {
 *         functionalTestClasspath {
 *              pluginSourceSet project.sourceSets.customMain
 *              testSourceSets project.sourceSets.functionalTest
 *         }
 *     }
 * </pre>
 *
 * @see JavaGradlePluginPlugin
 * @since 2.13
 */
@Incubating
public class JavaGradlePluginExtension {

    private FunctionalTestClasspath functionalTestClasspath;

    public JavaGradlePluginExtension(Project project) {
        functionalTestClasspath = new FunctionalTestClasspath(project);
    }

    /**
     * Configures functional test classpath configuration options. The specified closure
     * delegates to an instance of {@link FunctionalTestClasspath}.
     *
     * @param configureClosure functional test classpath configuration options
     */
    public void functionalTestClasspath(Closure<?> configureClosure) {
        ConfigureUtil.configure(configureClosure, functionalTestClasspath);
    }

    /**
     * Returns the functional test classpath configuration options.
     *
     * @return the functional test classpath configuration options.
     */
    public FunctionalTestClasspath getFunctionalTestClasspath() {
        return functionalTestClasspath;
    }
}
