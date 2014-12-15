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
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.coffeescript.CoffeeScriptSourceSet;
import org.gradle.language.coffeescript.internal.DefaultCoffeeScriptSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.JavaScriptProcessResources;
import org.gradle.play.tasks.PlayCoffeeScriptCompile;

import java.io.File;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Plugin for adding coffeescript compilation to a Play application.  Applies the {@link org.gradle.play.plugins.PlayJavaScriptPlugin} and adds
 * a {@link org.gradle.language.coffeescript.CoffeeScriptSourceSet}.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
@Incubating
public class PlayCoffeeScriptPlugin {
    private static final String DEFAULT_COFFEESCRIPT_VERSION = "1.8.0";
    private static final String DEFAULT_RHINO_VERSION = "1.7R4";

    static String getDefaultCoffeeScriptDependencyNotation() {
        return String.format("org.coffeescript:coffee-script-js:%s@js", DEFAULT_COFFEESCRIPT_VERSION);
    }

    static String getDefaultRhinoDependencyNotation() {
        return String.format("org.mozilla:rhino:%s", DEFAULT_RHINO_VERSION);
    }

    @Mutate
    void createCoffeeScriptSourceSets(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
        for (PlayApplicationSpec playComponent : components.withType(PlayApplicationSpec.class)) {
            registerSourceSetFactory((ComponentSpecInternal) playComponent, serviceRegistry.get(Instantiator.class), serviceRegistry.get(FileResolver.class));

            CoffeeScriptSourceSet coffeeScriptSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("coffeeScriptAssets", CoffeeScriptSourceSet.class);
            coffeeScriptSourceSet.getSource().srcDir("app/assets");
            coffeeScriptSourceSet.getSource().include("**/*.coffee");
        }
    }

    // TODO:DAZ This should be done via a @LanguageType rule
    private void registerSourceSetFactory(ComponentSpecInternal playComponent, final Instantiator instantiator, final FileResolver fileResolver) {
        final FunctionalSourceSet functionalSourceSet = playComponent.getSources();
        NamedDomainObjectFactory<CoffeeScriptSourceSet> namedDomainObjectFactory = new NamedDomainObjectFactory<CoffeeScriptSourceSet>() {
            public CoffeeScriptSourceSet create(String name) {
                return instantiator.newInstance(DefaultCoffeeScriptSourceSet.class, name, functionalSourceSet.getName(), fileResolver);
            }
        };
        functionalSourceSet.registerFactory(CoffeeScriptSourceSet.class, namedDomainObjectFactory);
    }

    @BinaryTasks
    void createCoffeeScriptTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
        for (final CoffeeScriptSourceSet coffeeScriptSourceSet : binary.getSource().withType(CoffeeScriptSourceSet.class)) {
            if (((LanguageSourceSetInternal) coffeeScriptSourceSet).getMayHaveSources()) {
                String compileTaskName = createCoffeeScriptCompile(tasks, binary, buildDir, coffeeScriptSourceSet);
                createJavaScriptCompile(tasks, binary, buildDir, coffeeScriptSourceSet, compileTaskName);
            }
        }
    }

    private String createCoffeeScriptCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final File buildDir,
                                             final CoffeeScriptSourceSet coffeeScriptSourceSet) {
        final String compileTaskName = "compile" + capitalize(binary.getName()) + capitalize(coffeeScriptSourceSet.getName());
        tasks.create(compileTaskName, PlayCoffeeScriptCompile.class, new Action<PlayCoffeeScriptCompile>() {
            @Override
            public void execute(PlayCoffeeScriptCompile coffeeScriptCompile) {
                coffeeScriptCompile.setDestinationDir(outputDirectory(buildDir, binary, compileTaskName));
                coffeeScriptCompile.setSource(coffeeScriptSourceSet.getSource());
                // TODO:DAZ This is a workaround for ordering issues in setting coffeeScriptJs (need something like convention mapping)
                if (!coffeeScriptCompile.hasCustomCoffeeScriptJs()) {
                    coffeeScriptCompile.setCoffeeScriptJsNotation(getDefaultCoffeeScriptDependencyNotation());
                }
                coffeeScriptCompile.setRhinoClasspathNotation(getDefaultRhinoDependencyNotation());
            }
        });
        return compileTaskName;
    }

    private void createJavaScriptCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final File buildDir, final CoffeeScriptSourceSet coffeeScriptSourceSet,
                                         final String compileTaskName) {
        final String processTaskName = "process" + capitalize(binary.getName()) + capitalize(coffeeScriptSourceSet.getName());
        tasks.create(processTaskName, JavaScriptProcessResources.class, new Action<JavaScriptProcessResources>() {
            @Override
            public void execute(JavaScriptProcessResources processGeneratedJavascript) {
                processGeneratedJavascript.dependsOn(compileTaskName);
                processGeneratedJavascript.from(outputDirectory(buildDir, binary, compileTaskName));

                File coffeeScriptProcessOutputDirectory = outputDirectory(buildDir, binary, processTaskName);
                processGeneratedJavascript.setDestinationDir(coffeeScriptProcessOutputDirectory);
                binary.getAssets().builtBy(processGeneratedJavascript);
                binary.getAssets().addAssetDir(coffeeScriptProcessOutputDirectory);
            }
        });
    }

    private File outputDirectory(File buildDir, PlayApplicationBinarySpec binary, String taskName) {
        return new File(buildDir, String.format("%s/src/%s", binary.getName(), taskName));
    }
}
