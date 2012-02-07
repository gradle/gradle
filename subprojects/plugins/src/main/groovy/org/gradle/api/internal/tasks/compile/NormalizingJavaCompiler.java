/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.WorkResult;

import java.io.File;

/**
 * A {@link JavaCompiler} which does some normalization of the compile configuration before delegating to some other compiler.
 */
public class NormalizingJavaCompiler extends JavaCompilerSupport {
    private final JavaCompiler compiler;

    public NormalizingJavaCompiler(JavaCompiler compiler) {
        this.compiler = compiler;
    }

    public WorkResult execute() {
        // Scan the source and classpath and remember the results
        source = new SimpleFileCollection(source.getFiles());
        classpath = new SimpleFileCollection(Lists.newArrayList(classpath));

        for (File file : source) {
            if (!file.getName().endsWith(".java")) {
                throw new InvalidUserDataException(String.format("Cannot compile non-Java source file '%s'.", file));
            }
        }
        configure(compiler);
        return compiler.execute();
    }
}
