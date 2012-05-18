/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.coffeescript;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.plugins.javascript.coffeescript.compile.internal.DefaultCoffeeScriptCompileSpec;

import java.io.File;

public class CoffeeScriptCompile extends SourceTask {

    private Object coffeeScriptJs;
    private Object destinationDir;
    private CoffeeScriptCompileOptions options = new CoffeeScriptCompileOptions();
    private CoffeeScriptCompiler compiler;

    @InputFiles
    public FileCollection getCoffeeScriptJs() {
        return getProject().files(coffeeScriptJs);
    }

    public void setCoffeeScriptJs(Object coffeeScriptJs) {
        this.coffeeScriptJs = coffeeScriptJs;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return getProject().file(destinationDir);
    }

    public void setDestinationDir(Object destinationDir) {
        this.destinationDir = destinationDir;
    }

    public CoffeeScriptCompileOptions getOptions() {
        return options;
    }

    public void options(Action<CoffeeScriptCompileOptions> action) {
        action.execute(getOptions());
    }

    public void options(Closure<?> closure) {
        getProject().configure(getOptions(), closure);
    }

    public CoffeeScriptCompiler getCompiler() {
        return compiler;
    }

    public void setCompiler(CoffeeScriptCompiler compiler) {
        this.compiler = compiler;
    }

    @TaskAction
    public void doCompile() {
        CoffeeScriptCompileSpec spec = new DefaultCoffeeScriptCompileSpec();
        spec.setCoffeeScriptJs(getCoffeeScriptJs().getSingleFile());
        spec.setDestinationDir(getDestinationDir());
        spec.setSource(getSource());
        spec.setOptions(getOptions());
        setDidWork(getCompiler().compile(spec).getDidWork());
    }
}
