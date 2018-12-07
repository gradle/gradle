/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.java.JavaPlatform;

/**
 * The Java platform plugin allows building platform components
 * for Java, which are usually published as BOM files (for Maven)
 * or Gradle platforms (Gradle metadata).
 *
 * @since 5.2
 */
@Incubating
public class JavaPlatformPlugin implements Plugin<Project> {
    public static final String API_CONFIGURATION_NAME = "api";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String CLASSPATH_CONFIGURATION_NAME = "classpath";

    private static final Action<Configuration> AS_CONSUMABLE_CONFIGURATION = new Action<Configuration>() {
        @Override
        public void execute(Configuration conf) {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(true);
        }
    };

    private static final Action<Configuration> AS_RESOLVABLE_CONFIGURATION = new Action<Configuration>() {
        @Override
        public void execute(Configuration conf) {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
        }
    };

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        createConfigurations(project);
        createSoftwareComponent(project);
    }

    private boolean createSoftwareComponent(Project project) {
        return project.getComponents().add(project.getObjects().newInstance(JavaPlatform.class, project.getConfigurations()));
    }

    private void createConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration api = configurations.create(API_CONFIGURATION_NAME, AS_CONSUMABLE_CONFIGURATION);
        Configuration runtime = project.getConfigurations().create(RUNTIME_CONFIGURATION_NAME, AS_CONSUMABLE_CONFIGURATION);
        runtime.extendsFrom(api);
        Configuration classpath = configurations.create(CLASSPATH_CONFIGURATION_NAME, AS_RESOLVABLE_CONFIGURATION);
        classpath.extendsFrom(runtime);
        classpath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
    }
}
