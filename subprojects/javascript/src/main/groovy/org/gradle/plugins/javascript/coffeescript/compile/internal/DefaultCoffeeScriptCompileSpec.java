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

package org.gradle.plugins.javascript.coffeescript.compile.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompileOptions;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompileSpec;

import java.io.File;

public class DefaultCoffeeScriptCompileSpec implements CoffeeScriptCompileSpec {

    private File coffeeScriptJs;
    private File destinationDir;
    private FileCollection source;
    private CoffeeScriptCompileOptions options;

    public File getCoffeeScriptJs() {
        return coffeeScriptJs;
    }

    public void setCoffeeScriptJs(File coffeeScriptJs) {
        this.coffeeScriptJs = coffeeScriptJs;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public CoffeeScriptCompileOptions getOptions() {
        return options;
    }

    public void setOptions(CoffeeScriptCompileOptions options) {
        this.options = options;
    }
}
