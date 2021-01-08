/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.scala.internal;

import org.gradle.api.file.RegularFile;
import org.gradle.internal.process.ArgWriter;
import org.gradle.workers.WorkAction;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class GenerateScaladoc implements WorkAction<ScaladocParameters> {
    @Override
    public void execute() {
        ScaladocParameters parameters = getParameters();
        Path optionsFile = parameters.getOptionsFile().map(RegularFile::getAsFile).map(File::toPath).getOrNull();
        try {
            List<String> args = generateArgList(parameters, optionsFile);
            invokeScalaDoc(args);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException("Could not generate scaladoc", e);
        } finally {
            // Try to clean-up generated options file
            if (optionsFile != null) {
                optionsFile.toFile().delete();
            }
        }
    }

    private List<String> generateArgList(ScaladocParameters parameters, Path optionsFile) {
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(parameters.getOutputDirectory().get().getAsFile().getAbsolutePath());

        args.add("-classpath");
        args.add(parameters.getClasspath().getAsPath());

        args.addAll(parameters.getOptions().get());

        List<String> sourceFiles = new ArrayList<>();
        for (File sourceFile : parameters.getSources().getAsFileTree().getFiles()) {
            sourceFiles.add(sourceFile.getAbsolutePath());
        }
        args.addAll(sourceFiles);

        if (optionsFile != null) {
            ArgWriter.argsFileGenerator(optionsFile.toFile(), ArgWriter.javaStyleFactory()).transform(args);
        }

        return args;
    }

    private void invokeScalaDoc(List<String> args) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> scaladocClass = Thread.currentThread().getContextClassLoader().loadClass("scala.tools.nsc.ScalaDoc");
        Method process = scaladocClass.getMethod("process", String[].class);
        Object scaladoc = scaladocClass.getDeclaredConstructor().newInstance();
        process.invoke(scaladoc, new Object[]{args.toArray(new String[0])});
    }
}
