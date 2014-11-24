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

package org.gradle.play.internal.coffeescript;

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class DefaultCoffeeScriptCompileSpec implements CoffeeScriptCompileSpec, Serializable {
    private final FileCollection source;
    private final File outputDirectory;

    private static final String DEFAULT_COFFEESCRIPT_VERSION = "1.7.1";
    private static final String DEFAULT_TRIREME_VERSION = "0.7.5";

    public DefaultCoffeeScriptCompileSpec(FileCollection source, File outputDirectory) {
        this.source = source;
        this.outputDirectory = outputDirectory;
    }

    public FileCollection getSource() {
        return source;
    }

    public File getDestinationDir() {
        return outputDirectory;
    }

    public List<String> getClassLoaderPackages() {
        return Arrays.asList("io.apigee.trireme", "META-INF");
    }

    public List<String> getCoffeeScriptDependencyNotations() {
        return Lists.newArrayList(
                String.format("org.webjars:coffee-script:%s", DEFAULT_COFFEESCRIPT_VERSION),
                String.format("io.apigee.trireme:trireme-core:%s", DEFAULT_TRIREME_VERSION),
                String.format("io.apigee.trireme:trireme-node10src:%s", DEFAULT_TRIREME_VERSION)
        );
    }
}
