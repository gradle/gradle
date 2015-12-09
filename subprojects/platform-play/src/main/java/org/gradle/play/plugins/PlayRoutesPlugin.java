/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.routes.RoutesSourceSet;
import org.gradle.language.routes.internal.DefaultRoutesSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.RoutesCompile;

import java.io.File;
import java.util.ArrayList;

/**
 * Plugin for compiling Play routes sources in a Play application.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayRoutesPlugin extends RuleSource {

    @LanguageType
    void registerRoutesLanguageType(LanguageTypeBuilder<RoutesSourceSet> builder) {
        builder.setLanguageName("routes");
        builder.defaultImplementation(DefaultRoutesSourceSet.class);
    }

    @Mutate
    void createGeneratedScalaSourceSets(ModelMap<PlayApplicationBinarySpecInternal> binaries, final ServiceRegistry serviceRegistry) {
        final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
        binaries.all(new Action<PlayApplicationBinarySpecInternal>() {
            @Override
            public void execute(PlayApplicationBinarySpecInternal playApplicationBinarySpec) {
                for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getInputs().withType(RoutesSourceSet.class)) {
                    playApplicationBinarySpec.addGeneratedScala(languageSourceSet, fileResolver);
                }
            }
        });
    }

    @BinaryTasks
    void createRoutesCompileTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpecInternal binary, @Path("buildDir") final File buildDir) {
        for (final RoutesSourceSet routesSourceSet : binary.getInputs().withType(RoutesSourceSet.class)) {
            final String routesCompileTaskName = binary.getTasks().taskName("compile", routesSourceSet.getName());
            final File routesCompilerOutputDirectory = binary.getNamingScheme().getOutputDirectory(buildDir, routesCompileTaskName);

            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                public void execute(RoutesCompile routesCompile) {
                    routesCompile.setDescription("Generates routes for the '" + routesSourceSet.getName() + "' source set.");
                    routesCompile.setPlatform(binary.getTargetPlatform());
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(routesSourceSet.getSource());
                    routesCompile.setOutputDirectory(routesCompilerOutputDirectory);
                    routesCompile.setInjectedRoutesGenerator(binary.getApplication().getInjectedRoutesGenerator());

                    ScalaLanguageSourceSet routesScalaSources = binary.getGeneratedScala().get(routesSourceSet);
                    routesScalaSources.getSource().srcDir(routesCompilerOutputDirectory);
                    routesScalaSources.builtBy(routesCompile);
                }
            });
        }
    }
}
