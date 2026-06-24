/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.checkstyle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.checkstyle.dsl.CheckstyleTool;

import java.util.Map;

/**
 * The project-wide half of the checkstyle demo: reacts to a {@code checkstyle { }} block on a
 * {@code javaLibrary} by resolving the requested {@code checkstyleVersion} to a single shared Checkstyle
 * tool classpath (adapting {@code AbstractCodeQualityPlugin.createConfigurations}, since there is no
 * {@code CheckstylePlugin} in the XDCL world) and publishing it on a {@link CheckstyleModel} project
 * extension. The per-source-set {@link CheckstyleReaction} reads that classpath back.
 *
 * <p>Stateless per the {@link Reaction} contract; idempotent via the model's presence.
 */
public class CheckstyleToolReaction implements Reaction<CheckstyleTool, Project> {

    /** The dependency-scope configuration the tool dependency is declared on. */
    private static final String LIBRARIES = "checkstyle";

    /** The resolvable configuration the shared classpath is resolved from. */
    private static final String CLASSPATH = "checkstyleClasspath";

    @Override
    public void on(CheckstyleTool data, Project project, ReactionScope scope) {
        if (project.getExtensions().findByType(CheckstyleModel.class) != null) {
            return; // already configured — reactions may run more than once
        }

        String version = data.checkstyleVersion().get();

        // The shared Checkstyle tool classpath, split into the two configuration roles (as
        // DependencyScopes does): a dependency-scope configuration carries the requested tool version; a
        // resolvable configuration extends it (runtime usage, runtime-provided libraries excluded) and
        // is published on the model. Resolved lazily, at task execution.
        project.getConfigurations().dependencyScope(LIBRARIES, configuration ->
            configuration.setDescription("The Checkstyle libraries to use for this project."));
        project.getDependencies().add(LIBRARIES, "com.puppycrawl.tools:checkstyle:" + version);

        ObjectFactory objects = project.getObjects();
        Configuration checkstyleClasspath = project.getConfigurations().resolvable(CLASSPATH, configuration -> {
            configuration.setDescription("The resolved Checkstyle classpath for this project.");
            configuration.extendsFrom(project.getConfigurations().getByName(LIBRARIES));
            // The runtime-classpath attribute set (what JvmPluginServices.configureAsRuntimeClasspath
            // would apply): Usage alone is ambiguous for modules that publish both a standard-JVM and an
            // Android runtime variant (e.g. Guava), so TargetJvmEnvironment=standard-jvm disambiguates.
            configuration.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
                attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
            });
            // Don't need these things, they're provided by the runtime.
            configuration.exclude(excludeProperties("ant", "ant"));
            configuration.exclude(excludeProperties("org.apache.ant", "ant"));
            configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"));
            configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"));
            configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"));
            configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"));
            configuration.exclude(excludeProperties("commons-logging", "commons-logging"));
            configuration.exclude(excludeProperties("log4j", "log4j"));
        }).get();

        CheckstyleModel model = project.getExtensions().create("checkstyleModel", CheckstyleModel.class);
        model.getCheckstyleClasspath().from(checkstyleClasspath);

        project.getLogger().lifecycle("checkstyle version=" + version);
    }

    private static Map<String, String> excludeProperties(String group, String module) {
        return Map.of("group", group, "module", module);
    }
}
