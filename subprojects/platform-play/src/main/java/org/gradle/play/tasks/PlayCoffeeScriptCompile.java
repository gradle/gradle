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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompile;

import java.io.File;

/**
 * Task for compiling CoffeeScript sources
 */
public class PlayCoffeeScriptCompile extends CoffeeScriptCompile {
    private File outputDirectory;

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
        super.setDestinationDir(new File(outputDirectory, "public"));
    }

    @Override
    public void setDestinationDir(Object destinationDir) {
        throw new UnsupportedOperationException("setDestinationDir not supported for PlayCoffeeScriptCompile - use setOutputDirectory instead");
    }

    public void setCoffeeScriptDependency(String notation) {
        setCoffeeScriptJs(getDetachedConfiguration(notation));
    }

    public void setRhinoDependency(String notation) {
        setRhinoClasspath(getDetachedConfiguration(notation));
    }

    private Configuration getDetachedConfiguration(String notation) {
        Dependency dependency = getProject().getDependencies().create(notation);
        Configuration configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        return configuration;
    }

}
