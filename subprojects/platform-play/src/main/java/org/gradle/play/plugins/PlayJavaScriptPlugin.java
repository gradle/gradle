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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.JavaScriptProcessResources;

import java.io.File;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Plugin for adding javascript processing to a Play application.  Registers "javascript" language support with the {@link org.gradle.language.javascript.JavaScriptSourceSet}.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
public class PlayJavaScriptPlugin {
    @Mutate
    void createJavascriptSourceSets(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
        for (PlayApplicationSpec playComponent : components.withType(PlayApplicationSpec.class)) {
            registerSourceSetFactory((ComponentSpecInternal) playComponent, serviceRegistry.get(Instantiator.class), serviceRegistry.get(FileResolver.class));
            JavaScriptSourceSet javaScriptSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("javaScriptSources", JavaScriptSourceSet.class);
            javaScriptSourceSet.getSource().srcDir("app/assets");
            javaScriptSourceSet.getSource().include("**/*.js");
        }
    }

    // TODO:DAZ This should be done via a @LanguageType rule
    private void registerSourceSetFactory(ComponentSpecInternal playComponent, final Instantiator instantiator, final FileResolver fileResolver) {
        final FunctionalSourceSet functionalSourceSet = playComponent.getSources();
        NamedDomainObjectFactory<JavaScriptSourceSet> namedDomainObjectFactory = new NamedDomainObjectFactory<JavaScriptSourceSet>() {
            public JavaScriptSourceSet create(String name) {
                return instantiator.newInstance(DefaultJavaScriptSourceSet.class, name, functionalSourceSet.getName(), fileResolver);
            }
        };
        functionalSourceSet.registerFactory(JavaScriptSourceSet.class, namedDomainObjectFactory);
    }

    @Mutate
    void createJavaScriptTasks(TaskContainer tasks, BinaryContainer binaryContainer, final ServiceRegistry serviceRegistry, final File buildDir) {
        for (PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
            for (JavaScriptSourceSet javaScriptSourceSet : binary.getSource().withType(JavaScriptSourceSet.class)) {

                LanguageSourceSetInternal sourceSetInternal = (LanguageSourceSetInternal) javaScriptSourceSet;

                if (sourceSetInternal.getMayHaveSources()) {
                    File javascriptOutputDirectory = new File(buildDir, String.format("%s/javascript", binary.getName()));
                    String processTaskName = "process" + capitalize(binary.getName()) + capitalize(sourceSetInternal.getFullName());
                    JavaScriptProcessResources javaScriptProcessResources = tasks.create(processTaskName, JavaScriptProcessResources.class);
                    javaScriptProcessResources.from(javaScriptSourceSet.getSource());
                    javaScriptProcessResources.setDestinationDir(javascriptOutputDirectory);

                    binary.getAssets().builtBy(javaScriptProcessResources);
                    binary.getAssets().addAssetDir(javascriptOutputDirectory);
                }
            }
        }
    }
}
