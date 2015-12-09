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
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.twirl.TwirlSourceSet;
import org.gradle.language.twirl.internal.DefaultTwirlSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.TwirlCompile;

import java.io.File;

/**
 * Plugin for compiling Twirl sources in a Play application.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayTwirlPlugin extends RuleSource {

    @LanguageType
    void registerTwirlLanguageType(LanguageTypeBuilder<TwirlSourceSet> builder) {
        builder.setLanguageName("twirl");
        builder.defaultImplementation(DefaultTwirlSourceSet.class);
    }

    @Mutate
    void createGeneratedScalaSourceSets(ModelMap<PlayApplicationBinarySpecInternal> binaries, final ServiceRegistry serviceRegistry) {
        final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
        binaries.all(new Action<PlayApplicationBinarySpecInternal>() {
            @Override
            public void execute(PlayApplicationBinarySpecInternal playApplicationBinarySpec) {
                for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getInputs().withType(TwirlSourceSet.class)) {
                    playApplicationBinarySpec.addGeneratedScala(languageSourceSet, fileResolver);
                }
            }
        });
    }

    @BinaryTasks
    void createTwirlCompileTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpecInternal binary, @Path("buildDir") final File buildDir) {
        for (final TwirlSourceSet twirlSourceSet : binary.getInputs().withType(TwirlSourceSet.class)) {
            final String twirlCompileTaskName = binary.getTasks().taskName("compile", twirlSourceSet.getName());
            final File twirlCompileOutputDirectory = binary.getNamingScheme().getOutputDirectory(buildDir, twirlCompileTaskName);

            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                public void execute(TwirlCompile twirlCompile) {
                    twirlCompile.setDescription("Compiles twirl templates for the '" + twirlSourceSet.getName() + "' source set.");
                    twirlCompile.setPlatform(binary.getTargetPlatform());
                    twirlCompile.setSource(twirlSourceSet.getSource());
                    twirlCompile.setOutputDirectory(twirlCompileOutputDirectory);

                    ScalaLanguageSourceSet twirlScalaSources = binary.getGeneratedScala().get(twirlSourceSet);
                    twirlScalaSources.getSource().srcDir(twirlCompileOutputDirectory);
                    twirlScalaSources.builtBy(twirlCompile);
                }
            });
        }
    }
}
