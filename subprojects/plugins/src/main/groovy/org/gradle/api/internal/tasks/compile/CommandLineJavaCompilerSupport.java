/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base class for implementing <tt>JavaCompiler</tt>s
 * that need to generate command-line options.
 */
public abstract class CommandLineJavaCompilerSupport extends JavaCompilerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineJavaCompilerSupport.class);
    
    protected List<String> generateCommandLineOptions() {
        List<String> options = new ArrayList<String>();
        if (sourceCompatibility != null) {
            options.add("-source");
            options.add(sourceCompatibility);
        }
        if (targetCompatibility != null) {
            options.add("-target");
            options.add(targetCompatibility);
        }
        if (destinationDir != null) {
            options.add("-d");
            options.add(destinationDir.getPath());
        }
        if (compileOptions.isVerbose()) {
            options.add("-verbose");
        }
        if (compileOptions.isDeprecation()) {
            options.add("-deprecation");
        }
        if (!compileOptions.isWarnings()) {
            options.add("-nowarn");
        }
        if (!compileOptions.isDebug()) {
            options.add("-g:none");
        }
        if (compileOptions.getEncoding() != null) {
            options.add("-encoding");
            options.add(compileOptions.getEncoding());
        }
        if (compileOptions.getBootClasspath() != null) {
            options.add("-bootclasspath");
            options.add(compileOptions.getBootClasspath());
        }
        if (compileOptions.getExtensionDirs() != null) {
            options.add("-extdirs");
            options.add(compileOptions.getExtensionDirs());
        }
        if (classpath != null && classpath.iterator().hasNext()) {
            options.add("-classpath");
            options.add(toFileCollection(classpath).getAsPath());
        }
        if (compileOptions.getCompilerArgs() != null) {
            options.addAll(compileOptions.getCompilerArgs());
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Invoking Java compiler with options '{}'", Joiner.on(' ').join(options));
        }

        return options;
    }

    private FileCollection toFileCollection(Iterable<File> classpath) {
        if (classpath instanceof FileCollection) {
            return (FileCollection) classpath;
        }
        return new SimpleFileCollection(Lists.newArrayList(classpath));
    }
}
