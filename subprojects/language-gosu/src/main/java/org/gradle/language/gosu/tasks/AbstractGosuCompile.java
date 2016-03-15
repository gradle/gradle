/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.tasks;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.gosu.DefaultGosuCompileSpec;
import org.gradle.api.internal.tasks.gosu.DefaultGosuCompileSpecFactory;
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * An abstract Gosu compile task sharing common functionality for compiling gosu.
 */
@Incubating
abstract public class AbstractGosuCompile extends AbstractCompile {
    protected static final Logger LOGGER = Logging.getLogger(AbstractGosuCompile.class);
    private final BaseGosuCompileOptions gosuCompileOptions;
    private final CompileOptions compileOptions = new CompileOptions();

    protected AbstractGosuCompile(BaseGosuCompileOptions gosuCompileOptions) {
        this.gosuCompileOptions = gosuCompileOptions;
    }

    /**
     * Returns the Gosu compilation options.
     */
    @Nested
    public BaseGosuCompileOptions getGosuCompileOptions() {
        return gosuCompileOptions;
    }

    /**
     * Returns the Java compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    abstract protected org.gradle.language.base.internal.compile.Compiler<GosuCompileSpec> getCompiler(GosuCompileSpec spec);

    abstract public Closure<FileCollection> getOrderClasspath();

    /**
     * Normally setting this value is not required.
     * Certain projects relying on depth-first resolution of module dependencies can use this
     * Closure to reorder the classpath as needed.
     *
     * @param orderClasspath a Closure returning a classpath to be passed to the GosuCompile task
     */
    abstract public void setOrderClasspath(Closure<FileCollection> orderClasspath);

    @TaskAction
    protected void compile() {
        GosuCompileSpec spec = createSpec();
        getCompiler(spec).execute(spec);
    }

    /**
     * Unique to Gosu, whose compiler API requires a list of source directories
     * @return the Set of srcDirs specified for this compile task
     */
    public Set<File> getSourceRoots() {
        Set<File> returnValues = new HashSet<File>();
        for(Object obj : this.source) {
            if(obj instanceof DefaultSourceDirectorySet) {
                returnValues.addAll(((DefaultSourceDirectorySet) obj).getSrcDirs());
            }
        }
        return returnValues;
    }

    protected GosuCompileSpec createSpec() {
        DefaultGosuCompileSpec spec = new DefaultGosuCompileSpecFactory(compileOptions).create();
        spec.setSource(getSource());
        spec.setSourceRoots(getSourceRoots());
        spec.setDestinationDir(getDestinationDir());
//        spec.setWorkingDir(getProject().getProjectDir());
//        spec.setTempDir(getTemporaryDir());
        if (getOrderClasspath() == null) {
            spec.setClasspath(getClasspath());
        } else {
            spec.setClasspath(getOrderClasspath().call(getProject(), getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)));
        }
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(getOptions());
        spec.setGosuCompileOptions(gosuCompileOptions);
        return spec;
    }
}
