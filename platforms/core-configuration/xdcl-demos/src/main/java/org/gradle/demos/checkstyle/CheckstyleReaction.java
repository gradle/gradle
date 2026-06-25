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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.checkstyle.dsl.Checkstyle;
import org.gradle.demos.java.JavaClasses;
import org.gradle.demos.java.JavaLibraryModel;
import org.gradle.demos.java.dsl.HasJavaSources;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * The per-source-set half of the checkstyle demo: reacts to a {@code checkstyle { }} block declared on
 * a Java source set and registers a {@code check<Name>Checkstyle} task. It owns only the per-task
 * configuration — {@code configFile} and {@code ignoreFailures} — and the wiring of the task; the
 * shared tool classpath comes from the project-wide {@link CheckstyleToolReaction} via the
 * {@link CheckstyleModel} it publishes.
 *
 * <p>It fires once per source set that opts in, recovering the host source-set name through
 * {@link ReactionScope#ancestor(Class)} — the binding host is the {@link HasJavaSources} trait the
 * source set composes. The Java sources to analyse come from the {@link JavaClasses} entry the Java
 * reaction publishes on its {@link JavaLibraryModel} (its {@code inputSources}), looked up by that name.
 *
 * <p>Ordering: a record's properties are walked before its own extensions, so this per-source reaction
 * fires <em>before</em> the {@code javaLibrary}-level {@link CheckstyleToolReaction}. The shared
 * classpath is therefore wired <em>lazily</em> (a {@code Callable}-backed file collection) and read at
 * task execution, by which point the tool reaction has published the model — or, if no
 * {@code checkstyle { }} block was declared on the {@code javaLibrary}, a clear error is raised.
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

        // Analyse the source set's input sources from the JavaClasses build model the Java reaction
        // published (mirrors the original's context.getBuildModel(parentDefinition).inputSources).
        JavaClasses classes = project.getExtensions().getByType(JavaLibraryModel.class).getClasses().getByName(name);

        // The declared (or conventional) config file; its parent is the config directory checkstyle
        // resolves config_loc-relative resources against.
        File configFile = project.file(data.configFile().get());
        boolean ignoreFailures = data.ignoreFailures().get();

        // The shared tool classpath is published by the javaLibrary-level CheckstyleToolReaction, so read
        // it lazily — resolved at task execution. A missing CheckstyleModel means no checkstyle { } block
        // set the version on the javaLibrary; fail with a clear message.
        FileCollection checkstyleClasspath = project.files((Callable<Object>) () -> {
            CheckstyleModel model = project.getExtensions().findByType(CheckstyleModel.class);
            if (model == null) {
                throw new GradleException("Source set '" + name + "' opted into checkstyle, but the javaLibrary "
                    + "declares no checkstyle { } block to set the Checkstyle version.");
            }
            return model.getCheckstyleClasspath();
        });

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

        project.getLogger().lifecycle("checkstyle[" + name + "]");
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
