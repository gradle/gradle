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

package org.gradle.demos.instrumentation;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.instrumentation.dsl.Instrument;
import org.gradle.demos.java.JavaClasses;
import org.gradle.demos.java.JavaLibraryModel;
import org.gradle.demos.java.dsl.HasJavaSources;

/**
 * The XDCL port of {@code InstrumentClassesProjectFeaturePlugin.Binding.ApplyAction}: reacts to an
 * {@code instrument { }} block declared on a Java source set and registers an {@code instrument<Name>Classes}
 * task that consumes that source set's compiled bytecode.
 *
 * <p>The reaction's data is the {@link Instrument} <em>facade</em> (the schema type). It fires once per
 * source set that opts in, recovering the host source-set name through {@link ReactionScope#ancestor(Class)}
 * — the binding host is the {@link HasJavaSources} trait the source set composes. The classes to instrument
 * come from the {@link JavaClasses} entry the Java reaction publishes on its {@link JavaLibraryModel} (its
 * {@code classesDir}, the raw compiler output), looked up by that name.
 *
 * <p>It then redirects that entry's {@code byteCodeDir} — the canonical bytecode the {@code jar} and
 * {@code test} tasks consume — to its own output, so those tasks bundle/run the instrumented classes
 * instead of the raw compiler output (overriding the {@code byteCodeDir -> classesDir} convention the Java
 * reaction set).
 *
 * <p>Ordering: the {@code javaLibrary}-template reaction publishes the model and its per-source
 * {@link JavaClasses} entries before any {@link HasJavaSources} per-source reaction fires, so the eager
 * lookup resolves and the {@code byteCodeDir} override is observed by the already-registered jar/test tasks
 * at execution time.
 *
 * <p>Stateless per the {@link Reaction} contract; idempotent via the registered task's presence.
 */
public class InstrumentReaction implements Reaction<Instrument, Project> {

    @Override
    public void on(Instrument data, Project project, ReactionScope scope) {
        HasJavaSources host = scope.ancestor(HasJavaSources.class)
            .orElseThrow(() -> new IllegalStateException("instrument { } must be declared on a Java source set"));
        String name = host.name().get();
        String cap = capitalize(name);

        String taskName = "instrument" + cap + "Classes";
        if (project.getTasks().getNames().contains(taskName)) {
            return; // already configured for this source set — reactions may run more than once
        }

        String configFile = data.configFile().getOrNull();
        String destinationDir = data.destinationDir().getOrNull();

        // The source set's JavaClasses entry, published by the javaLibrary-template reaction. That reaction
        // fires before this per-source one (a record's properties are walked before its extensions), so the
        // entry already exists — looked up eagerly because we must mutate its byteCodeDir below.
        JavaClasses classes = project.getExtensions().getByType(JavaLibraryModel.class).getClasses().getByName(name);

        TaskProvider<InstrumentClasses> instrument = project.getTasks().register(taskName, InstrumentClasses.class, task -> {
            task.setGroup("build");
            task.setDescription("Instruments the " + name + " classes.");
            // Consume the raw compiler output; classesDir carries the implicit compile-task dependency.
            task.getClassesDir().set(classes.getClassesDir());

            if (configFile != null) {
                task.getConfigFile().set(project.getLayout().getProjectDirectory().file(configFile));
            }
            if (destinationDir != null) {
                task.getInstrumentedClassesDir().set(project.getLayout().getProjectDirectory().dir(destinationDir));
            } else {
                task.getInstrumentedClassesDir().set(project.getLayout().getBuildDirectory().dir("instrumented/" + name));
            }
        });

        // Redirect the source set's canonical bytecode to the instrumented output, overriding the
        // byteCodeDir -> classesDir convention the Java reaction set. Downstream consumers that read
        // byteCodeDir (the jar and test tasks) therefore bundle/run the instrumented classes.
        classes.getByteCodeDir().set(instrument.flatMap(InstrumentClasses::getInstrumentedClassesDir));

        project.getLogger().lifecycle("instrument[" + name + "]");
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
