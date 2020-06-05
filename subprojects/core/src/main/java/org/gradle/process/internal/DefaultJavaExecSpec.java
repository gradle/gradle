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

package org.gradle.process.internal;

import com.google.common.collect.Iterables;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.process.internal.DefaultExecSpec.copyBaseExecSpecTo;


public class DefaultJavaExecSpec extends DefaultJavaForkOptions implements JavaExecSpec {

    private boolean ignoreExitValue;
    private InputStream standardInput;
    private OutputStream standardOutput;
    private OutputStream errorOutput;

    private final List<Object> arguments = new ArrayList<>();
    private final List<CommandLineArgumentProvider> argumentProviders = new ArrayList<>();

    private final Property<String> mainClass;
    private final Property<String> mainModule;
    private final ModularitySpec modularity;

    private final FileCollectionFactory fileCollectionFactory;
    private ConfigurableFileCollection classpath;

    @Inject
    public DefaultJavaExecSpec(
        ObjectFactory objectFactory,
        PathToFileResolver resolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        super(resolver, fileCollectionFactory, objectFactory.newInstance(DefaultJavaDebugOptions.class));
        this.mainClass = objectFactory.property(String.class);
        this.mainModule = objectFactory.property(String.class);
        this.modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        this.fileCollectionFactory = fileCollectionFactory;
        this.classpath = fileCollectionFactory.configurableFiles("classpath");
    }

    public void copyTo(JavaExecSpec targetSpec) {
        // JavaExecSpec
        targetSpec.setArgs(getArgs());
        targetSpec.getArgumentProviders().addAll(getArgumentProviders());
        targetSpec.getMainClass().set(getMainClass());
        targetSpec.getMainModule().set(getMainModule());
        targetSpec.getModularity().getInferModulePath().set(getModularity().getInferModulePath());
        targetSpec.classpath(getClasspath());
        // BaseExecSpec
        copyBaseExecSpecTo(this, targetSpec);
        // Java fork options
        super.copyTo(targetSpec);
    }

    @Override
    public List<String> getCommandLine() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(getExecutable());
        commandLine.addAll(getAllArguments());
        return commandLine;
    }

    private List<String> getAllArguments() {
        List<String> allArgs;
        List<String> args = getArgs();
        if (args == null) {
            allArgs = new ArrayList<>();
        } else {
            allArgs = new ArrayList<>(args);
        }
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(allArgs, argumentProvider.asArguments());
        }
        return allArgs;
    }

    @Override
    public JavaExecSpec args(Object... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        GUtil.addToCollection(arguments, args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable List<String> arguments) {
        this.arguments.clear();
        if (arguments != null) {
            this.arguments.addAll(arguments);
        }
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable Iterable<?> arguments) {
        this.arguments.clear();
        if (arguments != null) {
            GUtil.addToCollection(this.arguments, arguments);
        }
        return this;
    }

    @Nullable
    @Override
    public List<String> getArgs() {
        List<String> args = new ArrayList<>();
        for (Object argument : arguments) {
            args.add(argument.toString());
        }
        return args;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentProviders;
    }

    @Override
    public JavaExecSpec classpath(Object... paths) {
        this.classpath.from(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return classpath;
    }

    @Override
    public JavaExecSpec setClasspath(FileCollection classpath) {
        this.classpath = fileCollectionFactory.configurableFiles("classpath");
        this.classpath.setFrom(classpath);
        return this;
    }

    @Override
    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public JavaExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    @Override
    public InputStream getStandardInput() {
        return standardInput;
    }

    @Override
    public JavaExecSpec setStandardInput(InputStream standardInput) {
        this.standardInput = standardInput;
        return this;
    }

    @Override
    public OutputStream getStandardOutput() {
        return standardOutput;
    }

    @Override
    public JavaExecSpec setStandardOutput(OutputStream standardOutput) {
        this.standardOutput = standardOutput;
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return errorOutput;
    }

    @Override
    public JavaExecSpec setErrorOutput(OutputStream errorOutput) {
        this.errorOutput = errorOutput;
        return this;
    }

    @Override
    public Property<String> getMainClass() {
        return mainClass;
    }

    @Nullable
    @Override
    public String getMain() {
        return getMainClass().getOrNull();
    }

    @Override
    public JavaExecSpec setMain(@Nullable String main) {
        getMainClass().set(main);
        return this;
    }

    @Override
    public Property<String> getMainModule() {
        return mainModule;
    }

    @Override
    public ModularitySpec getModularity() {
        return modularity;
    }
}
