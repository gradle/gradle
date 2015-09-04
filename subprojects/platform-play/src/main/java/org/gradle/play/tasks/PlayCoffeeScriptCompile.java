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

package org.gradle.play.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.base.internal.tasks.StaleClassCleaner;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompile;

/**
 * Task for compiling CoffeeScript sources into JavaScript.
 */
@Incubating
public class PlayCoffeeScriptCompile extends CoffeeScriptCompile {
    public void setCoffeeScriptJsNotation(String notation) {
        super.setCoffeeScriptJs(getDetachedConfiguration(notation));
    }

    @Override
    public void setCoffeeScriptJs(Object coffeeScriptJs) {
        super.setCoffeeScriptJs(coffeeScriptJs);
    }

    public void setRhinoClasspathNotation(String notation) {
        setRhinoClasspath(getDetachedConfiguration(notation));
    }

    private Configuration getDetachedConfiguration(String notation) {
        Dependency dependency = getProject().getDependencies().create(notation);
        return getProject().getConfigurations().detachedConfiguration(dependency);
    }

    @Override
    public void doCompile() {
        StaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getDestinationDir());
        cleaner.execute();
        super.doCompile();
    }
}
