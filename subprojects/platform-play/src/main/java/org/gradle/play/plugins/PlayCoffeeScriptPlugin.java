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

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.coffeescript.CoffeeScriptSourceSet;
import org.gradle.language.coffeescript.internal.DefaultCoffeeScriptSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.internal.ComponentSpecInternal;
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
public class PlayCoffeeScriptPlugin implements Plugin<Project> {
    private static final String DEFAULT_COFFEESCRIPT_VERSION = "1.8.0";
    private static final String DEFAULT_RHINO_VERSION = "1.7R4";

    public void apply(Project project) {
        project.getPluginManager().apply(PlayPlugin.class);
    }

    static String getDefaultCoffeeScriptDependencyNotation() {
        return String.format("org.coffeescript:coffee-script-js:%s@js", DEFAULT_COFFEESCRIPT_VERSION);
    }

    static String getDefaultRhinoDependencyNotation() {
        return String.format("org.mozilla:rhino:%s", DEFAULT_RHINO_VERSION);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {

        @Mutate
        void createCoffeeScriptSourceSets(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
            for (PlayApplicationSpec playComponent : components.withType(PlayApplicationSpec.class)) {
                registerSourceSetFactory((ComponentSpecInternal) playComponent, serviceRegistry.get(Instantiator.class), serviceRegistry.get(FileResolver.class));

                CoffeeScriptSourceSet coffeeScriptSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("coffeeScriptSources", CoffeeScriptSourceSet.class);
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

        // TODO Can't use @BinaryTasks because there's no way to modify the task properties after it has been created with CollectionBuilder<Task>
        @Mutate
        void createCoffeeScriptTasks(TaskContainer tasks, BinaryContainer binaryContainer, final ServiceRegistry serviceRegistry, final File buildDir) {
            for (PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
                for (CoffeeScriptSourceSet coffeeScriptSourceSet : binary.getSource().withType(CoffeeScriptSourceSet.class)) {

                    LanguageSourceSetInternal sourceSetInternal = (LanguageSourceSetInternal) coffeeScriptSourceSet;

                    if (sourceSetInternal.getMayHaveSources()) {
                        File coffeeScriptCompileOutputDirectory = new File(buildDir, String.format("%s/coffeescript", binary.getName()));
                        String compileTaskName = "compile" + capitalize(binary.getName()) + capitalize(sourceSetInternal.getFullName());
                        PlayCoffeeScriptCompile coffeeScriptCompile = tasks.create(compileTaskName, PlayCoffeeScriptCompile.class);
                        coffeeScriptCompile.setDestinationDir(coffeeScriptCompileOutputDirectory);
                        coffeeScriptCompile.setSource(coffeeScriptSourceSet.getSource());
                        coffeeScriptCompile.setCoffeeScriptJsNotation(getDefaultCoffeeScriptDependencyNotation());
                        coffeeScriptCompile.setRhinoClasspathNotation(getDefaultRhinoDependencyNotation());
                        binary.getTasks().add(coffeeScriptCompile);

                        // TODO:DAZ Should not be sharing this output directory with non-coffeescript javascript (harder for task to be up-to-date)
                        File coffeeScriptProcessOutputDirectory = new File(buildDir, String.format("%s/javascript", binary.getName()));
                        String processTaskName = "process" + capitalize(binary.getName()) + capitalize(sourceSetInternal.getFullName());
                        JavaScriptProcessResources processGeneratedJavascript = tasks.create(processTaskName, JavaScriptProcessResources.class);
                        processGeneratedJavascript.dependsOn(coffeeScriptCompile);
                        processGeneratedJavascript.from(coffeeScriptCompile.getDestinationDir());
                        processGeneratedJavascript.setDestinationDir(coffeeScriptProcessOutputDirectory);
                        binary.getTasks().add(processGeneratedJavascript);

                        binary.getAssets().builtBy(processGeneratedJavascript);
                        binary.getAssets().addAssetDir(coffeeScriptProcessOutputDirectory);
                    }
                }
            }
        }
    }
}
