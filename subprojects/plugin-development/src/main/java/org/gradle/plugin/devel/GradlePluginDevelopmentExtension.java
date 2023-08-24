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

package org.gradle.plugin.devel;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.Arrays;
import java.util.Set;

/**
 * Configuration options for the {@link org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin}.
 *
 * <p>Below is a full configuration example. Since all properties have sensible defaults,
 * typically only selected properties will be configured.
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'java-gradle-plugin'
 *     }
 *
 *     sourceSets {
 *         customMain
 *         functionalTest
 *     }
 *
 *     gradlePlugin {
 *         pluginSourceSet project.sourceSets.customMain
 *         testSourceSets project.sourceSets.functionalTest
 *         plugins {
 *             helloPlugin {
 *                 id  = 'org.example.hello'
 *                 implementationClass = 'org.example.HelloPlugin'
 *             }
 *         }
 *     }
 * </pre>
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 * @since 2.13
 */
public abstract class GradlePluginDevelopmentExtension {
    private final SourceSetContainer testSourceSets;
    private SourceSet pluginSourceSet;
    private boolean automatedPublishing = true;

    public GradlePluginDevelopmentExtension(Project project, SourceSet pluginSourceSet, SourceSet testSourceSet) {
        this(project, pluginSourceSet, new SourceSet[] {testSourceSet});
    }

    public GradlePluginDevelopmentExtension(Project project, SourceSet pluginSourceSet, SourceSet[] testSourceSets) {
        this.pluginSourceSet = pluginSourceSet;
        this.testSourceSets = project.getObjects().newInstance(DefaultSourceSetContainer.class);
        testSourceSets(testSourceSets);
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
     * Adds some source sets to the collection which will be using TestKit.
     *
     * Calling this method multiple times with different source sets is <strong>additive</strong> - this method
     * will add to the existing collection of source sets.
     *
     * @param testSourceSet the test source set to add
     * @since 7.4
     */
    @Incubating
    public void testSourceSet(SourceSet testSourceSet) {
        this.testSourceSets.add(testSourceSet);
    }

    /**
     * Provides the source sets executing the functional tests with TestKit.
     * <p>
     * Calling this method multiple times with different source sets is <strong>NOT</strong> additive.  Calling this
     * method will overwrite any existing test source sets with the provided arguments.
     *
     * @param testSourceSets the test source sets
     */
    public void testSourceSets(SourceSet... testSourceSets) {
        this.testSourceSets.clear();
        this.testSourceSets.addAll(Arrays.asList(testSourceSets));
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
     * Returns the source sets executing the functional tests with TestKit. Defaults to {@code project.sourceSets.test}.
     *
     * @return the test source sets
     */
    public Set<SourceSet> getTestSourceSets() {
        return testSourceSets;
    }

    /**
     * Returns the property holding the URL for the plugin's website.
     *
     * @since 7.6
     */
    @Incubating
    public abstract Property<String> getWebsite();

    /**
     * Returns the property holding the URL for the plugin's VCS repository.
     *
     * @since 7.6
     */
    @Incubating
    public abstract Property<String> getVcsUrl();

    /**
     * Returns the declared plugins.
     *
     * @return the declared plugins, never null
     */
    public abstract NamedDomainObjectContainer<PluginDeclaration> getPlugins();

    /**
     * Configures the declared plugins.
     *
     * @param action the configuration action to invoke on the plugins
     */
    public void plugins(Action<? super NamedDomainObjectContainer<PluginDeclaration>> action) {
        action.execute(getPlugins());
    }

    /**
     * Whether the plugin should automatically configure the publications for the plugins.
     * @return true if publishing should be automated, false otherwise
     */
    public boolean isAutomatedPublishing() {
        return automatedPublishing;
    }

    /**
     * Configures whether the plugin should automatically configure the publications for the plugins.
     * @param automatedPublishing whether to automated publication
     */
    public void setAutomatedPublishing(boolean automatedPublishing) {
        this.automatedPublishing = automatedPublishing;
    }
}
