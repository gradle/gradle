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

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration options for setting up the functional test classpath.
 *
 * @see JavaGradlePluginExtension
 * @since 2.13
 */
@Incubating
public class FunctionalTestClasspath {

    private SourceSet pluginSourceSet;
    private Set<SourceSet> testSourceSets = new HashSet<SourceSet>();

    public FunctionalTestClasspath(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        pluginSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        testSourceSets(javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME));
    }

    /**
     * Provides the source set that compiles the code under test.
     *
     * @param pluginSourceSet the plugin source set
     */
    public void pluginSourceSet(SourceSet pluginSourceSet) {
        this.pluginSourceSet = pluginSourceSet;
    }

    /**
     * Provides the source sets executing the functional tests with TestKit.
     * <p>
     * Calling this method multiple times with different source sets is not additive.
     *
     * @param testSourceSets the test source sets
     */
    public void testSourceSets(SourceSet... testSourceSets) {
        this.testSourceSets = new HashSet<SourceSet>(Arrays.asList(testSourceSets));
    }

    /**
     * Returns the source set that compiles the code under test. Defaults to {@code project.sourceSets.main}.
     *
     * @return the plugin source set
     */
    public SourceSet getPluginSourceSet() {
        return pluginSourceSet;
    }

    /**
     * Returns the the source sets executing the functional tests with TestKit. Defaults to {@code project.sourceSets.test}.
     *
     * @return the test source sets
     */
    public Set<SourceSet> getTestSourceSets() {
        return testSourceSets;
    }
}
