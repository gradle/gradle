/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.collect.Iterables;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.Provider;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@NonNullApi
public class ExecHandleCommandLineCombiner {

    public static List<String> getCommandLine(String executable, List<String> allArgs) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(executable);
        commandLine.addAll(allArgs);
        return commandLine;
    }

    public static List<String> getAllArgs(List<String> allJvmArgs, @Nullable List<String> args, List<CommandLineArgumentProvider> argumentProviders) {
        List<String> allArgs = new ArrayList<>(allJvmArgs);
        if (args != null) {
            allArgs.addAll(args);
        }
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(allArgs, argumentProvider.asArguments());
        }
        return allArgs;
    }

    public static List<String> getAllJvmArgs(
        List<String> allJvmArgs,
        FileCollection realClasspath,
        Provider<String> mainClass,
        Provider<String> mainModule,
        ModularitySpec modularity,
        @Nullable JavaModuleDetector javaModuleDetector
    ) {
        List<String> allArgs = new ArrayList<>(allJvmArgs);
        boolean runAsModule = modularity.getInferModulePath().get() && mainModule.isPresent();

        if (runAsModule) {
            addModularJavaRunArgs(
                realClasspath,
                allArgs,
                mainClass,
                mainModule,
                modularity,
                javaModuleDetector
            );
        } else {
            addClassicJavaRunArgs(
                realClasspath,
                allArgs,
                mainClass
            );
        }

        return allArgs;
    }

    private static void addModularJavaRunArgs(
        FileCollection classpath,
        List<String> allArgs,
        Provider<String> mainClass,
        Provider<String> mainModule,
        ModularitySpec modularity,
        @Nullable JavaModuleDetector javaModuleDetector
    ) {
        if (javaModuleDetector == null) {
            throw new IllegalStateException("Running a Java module is not supported in this context.");
        }
        FileCollection rtModulePath = javaModuleDetector.inferModulePath(modularity.getInferModulePath().get(), classpath);
        FileCollection rtClasspath = javaModuleDetector.inferClasspath(modularity.getInferModulePath().get(), classpath);

        if (rtClasspath != null && !rtClasspath.isEmpty()) {
            allArgs.add("-cp");
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtClasspath));
        }
        if (rtModulePath != null && !rtModulePath.isEmpty()) {
            allArgs.add("--module-path");
            allArgs.add(CollectionUtils.join(File.pathSeparator, rtModulePath));
        }
        allArgs.add("--module");
        if (!mainClass.isPresent()) {
            allArgs.add(mainModule.get());
        } else {
            allArgs.add(mainModule.get() + "/" + mainClass.get());
        }
    }

    private static void addClassicJavaRunArgs(
        FileCollection classpath,
        List<String> allArgs,
        Provider<String> mainClass
    ) {
        if (!mainClass.isPresent()) {
            if (classpath != null && classpath.getFiles().size() == 1) {
                allArgs.add("-jar");
                allArgs.add(classpath.getSingleFile().getAbsolutePath());
            } else {
                throw new IllegalStateException("No main class specified and classpath is not an executable jar.");
            }
        } else {
            if (classpath != null && !classpath.isEmpty()) {
                allArgs.add("-cp");
                allArgs.add(CollectionUtils.join(File.pathSeparator, classpath));
            }
            allArgs.add(mainClass.get());
        }
    }
}
