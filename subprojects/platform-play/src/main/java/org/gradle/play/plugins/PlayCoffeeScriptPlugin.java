/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.coffeescript.CoffeeScriptSourceSet;
import org.gradle.language.coffeescript.internal.DefaultCoffeeScriptSourceSet;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.tasks.PlayCoffeeScriptCompile;

import java.io.File;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Plugin for adding coffeescript compilation to a Play application.  Adds support for
 * defining {@link org.gradle.language.coffeescript.CoffeeScriptSourceSet} source sets.  A
 * "coffeeScript" source set is created by default.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayCoffeeScriptPlugin extends RuleSource {
    private static final String DEFAULT_COFFEESCRIPT_VERSION = "1.8.0";
    private static final String DEFAULT_RHINO_VERSION = "1.7R4";

    static String getDefaultCoffeeScriptDependencyNotation() {
        return String.format("org.coffeescript:coffee-script-js:%s@js", DEFAULT_COFFEESCRIPT_VERSION);
    }

    static String getDefaultRhinoDependencyNotation() {
        return String.format("org.mozilla:rhino:%s", DEFAULT_RHINO_VERSION);
    }

    @LanguageType
    void registerCoffeeScript(LanguageTypeBuilder<CoffeeScriptSourceSet> builder) {
        builder.setLanguageName("coffeeScript");
        builder.defaultImplementation(DefaultCoffeeScriptSourceSet.class);
    }

    @Mutate
    void createCoffeeScriptSourceSets(ModelMap<PlayApplicationSpec> components) {
        components.beforeEach(new Action<PlayApplicationSpec>() {
            @Override
            public void execute(PlayApplicationSpec playComponent) {
                playComponent.getSources().create("coffeeScript", CoffeeScriptSourceSet.class, new Action<CoffeeScriptSourceSet>() {
                    @Override
                    public void execute(CoffeeScriptSourceSet coffeeScriptSourceSet) {
                        coffeeScriptSourceSet.getSource().srcDir("app/assets");
                        coffeeScriptSourceSet.getSource().include("**/*.coffee");
                    }
                });
            }
        });
    }

    @Mutate
    void createGeneratedJavaScriptSourceSets(ModelMap<PlayApplicationBinarySpec> binaries, final ServiceRegistry serviceRegistry) {
        final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
        final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        binaries.all(new Action<PlayApplicationBinarySpec>() {
            @Override
            public void execute(PlayApplicationBinarySpec playApplicationBinarySpec) {
                for (CoffeeScriptSourceSet coffeeScriptSourceSet : playApplicationBinarySpec.getInputs().withType(CoffeeScriptSourceSet.class)) {
                    JavaScriptSourceSet javaScriptSourceSet = BaseLanguageSourceSet.create(DefaultJavaScriptSourceSet.class, String.format("%sJavaScript", coffeeScriptSourceSet.getName()), playApplicationBinarySpec.getName(), fileResolver, instantiator);
                    playApplicationBinarySpec.getGeneratedJavaScript().put(coffeeScriptSourceSet, javaScriptSourceSet);
                }
            }
        });
    }

    @BinaryTasks
    void createCoffeeScriptTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpec binary, @Path("buildDir") final File buildDir) {
        tasks.beforeEach(PlayCoffeeScriptCompile.class, new Action<PlayCoffeeScriptCompile>() {
            @Override
            public void execute(PlayCoffeeScriptCompile coffeeScriptCompile) {
                coffeeScriptCompile.setRhinoClasspathNotation(getDefaultRhinoDependencyNotation());
                coffeeScriptCompile.setCoffeeScriptJsNotation(getDefaultCoffeeScriptDependencyNotation());
            }
        });

        for (final CoffeeScriptSourceSet coffeeScriptSourceSet : binary.getInputs().withType(CoffeeScriptSourceSet.class)) {
            if (((LanguageSourceSetInternal) coffeeScriptSourceSet).getMayHaveSources()) {
                final String compileTaskName = "compile" + capitalize(binary.getName()) + capitalize(coffeeScriptSourceSet.getName());
                tasks.create(compileTaskName, PlayCoffeeScriptCompile.class, new Action<PlayCoffeeScriptCompile>() {
                    @Override
                    public void execute(PlayCoffeeScriptCompile coffeeScriptCompile) {
                        coffeeScriptCompile.setDescription("Compiles coffeescript for the '" + coffeeScriptSourceSet.getName() + "' source set.");

                        File outputDirectory = outputDirectory(buildDir, binary, compileTaskName);
                        coffeeScriptCompile.setDestinationDir(outputDirectory);
                        coffeeScriptCompile.setSource(coffeeScriptSourceSet.getSource());

                        JavaScriptSourceSet javaScriptSourceSet = binary.getGeneratedJavaScript().get(coffeeScriptSourceSet);
                        javaScriptSourceSet.getSource().srcDir(outputDirectory);
                        javaScriptSourceSet.builtBy(coffeeScriptCompile);
                    }
                });
            }
        }
    }

    private File outputDirectory(File buildDir, PlayApplicationBinarySpec binary, String taskName) {
        return new File(buildDir, String.format("%s/src/%s", binary.getName(), taskName));
    }
}
