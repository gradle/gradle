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

package org.gradle.play.internal;

import org.gradle.api.Action;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.routes.RoutesSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.twirl.TwirlSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.play.PlayApplicationSpec;

public class PlaySourceSetRules extends RuleSource {

    @Defaults
    void createJvmSourceSets(PlayApplicationSpec playComponent) {
        playComponent.getSources().create("scala", ScalaLanguageSourceSet.class, new Action<ScalaLanguageSourceSet>() {
            @Override
            public void execute(ScalaLanguageSourceSet scalaSources) {
                scalaSources.getSource().srcDir("app");
                scalaSources.getSource().include("**/*.scala");
            }
        });

        playComponent.getSources().create("java", JavaSourceSet.class, new Action<JavaSourceSet>() {
            @Override
            public void execute(JavaSourceSet javaSources) {
                javaSources.getSource().srcDir("app");
                javaSources.getSource().include("**/*.java");
            }
        });

        playComponent.getSources().create("resources", JvmResourceSet.class, new Action<JvmResourceSet>() {
            @Override
            public void execute(JvmResourceSet appResources) {
                appResources.getSource().srcDirs("conf");
            }
        });
    }

    @Defaults
    void createTwirlSourceSets(PlayApplicationSpec playComponent) {
        playComponent.getSources().create("twirlTemplates", TwirlSourceSet.class, new Action<TwirlSourceSet>() {
            @Override
            public void execute(TwirlSourceSet twirlSourceSet) {
                twirlSourceSet.getSource().srcDir("app");
                twirlSourceSet.getSource().include("**/*.html");
            }
        });
    }

    @Defaults
    void createRoutesSourceSets(PlayApplicationSpec playComponent) {
        playComponent.getSources().create("routes", RoutesSourceSet.class, new Action<RoutesSourceSet>() {
            @Override
            public void execute(RoutesSourceSet routesSourceSet) {
                routesSourceSet.getSource().srcDir("conf");
                routesSourceSet.getSource().include("routes");
                routesSourceSet.getSource().include("*.routes");
            }
        });
    }
}
