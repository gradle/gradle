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
import org.gradle.demos.checkstyle.dsl.Checkstyle;
import org.gradle.demos.java.JavaClasses;
import org.gradle.demos.java.JavaLibraryModel;
import org.gradle.demos.java.dsl.HasJavaSources;

import java.io.File;
import java.util.Map;

/**
 * The XDCL analog of the project-features {@code CheckstyleProjectFeaturePlugin}: reacts to a
 * {@code checkstyle { }} block declared on a Java source set by resolving the requested
 * Checkstyle tool version to a classpath and registering a {@code check<Name>Checkstyle} task that
 * analyses that source set's Java sources.
 *
 * <p>The reaction's data is the {@link Checkstyle} <em>facade</em> (the schema type); the analysed task
 * type {@code org.gradle.api.plugins.quality.Checkstyle} shares the simple name and is referenced
 * fully-qualified throughout to avoid an import collision.
 *
 * <p>It fires once per source set that opts in (the block is opt-in, like the original feature), and
 * recovers the host source-set name through {@link ReactionScope#ancestor(Class)} — the binding host is
 * the {@link HasJavaSources} trait the source set composes (an extension host must be a trait or
 * template, not a bare record), and {@code name} is the per-source identity a {@code [String:
 * JavaSource]} map could not expose. The Java sources to analyse come from the {@link JavaClasses}
 * entry the Java reaction publishes on its {@link JavaLibraryModel} (its {@code inputSources}), looked
 * up by that name — the original's {@code context.getBuildModel(parentDefinition).inputSources}.
 * Stateless per the {@link Reaction} contract; idempotent via the registered task's presence (reactions
 * may run more than once).
 *
 * <p>Divergence from the original: alongside the original's {@code configFile} and
 * {@code ignoreFailures}, the demo adds a tool {@code checkstyleVersion} and resolves the tool classpath
 * itself — adapting {@code AbstractCodeQualityPlugin.createConfigurations} — because there is no
 * {@code CheckstylePlugin} in the XDCL world to supply it.
 */
public class CheckstyleReaction implements Reaction<Checkstyle, Project> {

    @Override
    public void on(Checkstyle data, Project project, ReactionScope scope) {
        HasJavaSources host = scope.ancestor(HasJavaSources.class)
            .orElseThrow(() -> new IllegalStateException("checkstyle { } must be declared on a Java source set"));
        String name = host.name().get();
        String cap = capitalize(name);

        String taskName = "check" + cap + "Checkstyle";
        if (project.getTasks().getNames().contains(taskName)) {
            return; // already configured for this source set — reactions may run more than once
        }

        String version = data.checkstyleVersion().get();

        // The Checkstyle tool classpath, split into the two configuration roles (as DependencyScopes
        // does for the demo's compile/runtime paths) — a resolvable cannot have dependencies declared
        // against it. A dependency-scope configuration carries the requested tool version; a resolvable
        // configuration extends it (runtime usage, runtime-provided libraries excluded — mirroring
        // AbstractCodeQualityPlugin.createConfigurations) and is wired into the task. Resolved lazily at
        // task execution, so it does not matter that the project repositories are configured by a
        // different (Java) reaction.
        String librariesName = name + "Checkstyle";
        project.getConfigurations().dependencyScope(librariesName, configuration ->
            configuration.setDescription("The Checkstyle libraries to use for the " + name + " source set."));
        project.getDependencies().add(librariesName, "com.puppycrawl.tools:checkstyle:" + version);

        ObjectFactory objects = project.getObjects();
        Configuration checkstyleClasspath = project.getConfigurations().resolvable(name + "CheckstyleClasspath", configuration -> {
            configuration.setDescription("The resolved Checkstyle classpath for the " + name + " source set.");
            configuration.extendsFrom(project.getConfigurations().getByName(librariesName));
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

        // Analyse the source set's input sources from the JavaClasses build model the Java reaction
        // published (mirrors the original's context.getBuildModel(parentDefinition).inputSources) — not a
        // location derived from the source-set name. The root template reaction dispatches before this
        // extension reaction, so the model and its per-source-set entry already exist.
        JavaClasses classes = project.getExtensions().getByType(JavaLibraryModel.class).getClasses().getByName(name);

        // The declared (or conventional) config file; its parent is the config directory checkstyle
        // resolves config_loc-relative resources against.
        File configFile = project.file(data.configFile().get());
        boolean ignoreFailures = data.ignoreFailures().get();

        project.getTasks().register(taskName, org.gradle.api.plugins.quality.Checkstyle.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs Checkstyle on the " + name + " source set.");
            task.setSource(classes.getInputSources());
            task.setCheckstyleClasspath(checkstyleClasspath);
            // The classpath of the analysed classes (for type resolution); empty is fine for the demo.
            task.setClasspath(project.files());
            task.setIgnoreFailures(ignoreFailures);
            // No CheckstylePlugin to supply conventions: take the config file from the schema.
            task.setConfigFile(configFile);
            task.getConfigDirectory().fileValue(configFile.getParentFile());
            // Enable both reports explicitly: setting only the output location does not mark a report
            // required, so a standalone task (no CheckstylePlugin conventions) would write nothing.
            task.getReports().getXml().getRequired().set(true);
            task.getReports().getXml().getOutputLocation()
                .set(project.getLayout().getBuildDirectory().file("reports/checkstyle/" + name + ".xml"));
            task.getReports().getHtml().getRequired().set(true);
            task.getReports().getHtml().getOutputLocation()
                .set(project.getLayout().getBuildDirectory().file("reports/checkstyle/" + name + ".html"));
        });

        project.getLogger().lifecycle("checkstyle[" + name + "] version=" + version);
    }

    private static Map<String, String> excludeProperties(String group, String module) {
        return Map.of("group", group, "module", module);
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
