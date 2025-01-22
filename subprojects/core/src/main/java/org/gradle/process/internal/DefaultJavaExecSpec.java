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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaExecSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;

import static org.gradle.process.internal.DefaultExecSpec.copyBaseExecSpecTo;


public abstract class DefaultJavaExecSpec extends DefaultJavaForkOptions implements JavaExecSpec {

    private final ProcessArgumentsSpec argumentsSpec = new ProcessArgumentsSpec(new ProcessArgumentsSpec.HasExecutable() {
        @Override
        public String getExecutable() {
            return DefaultJavaExecSpec.this.getExecutable().get();
        }

        @Override
        public void setExecutable(Object executable) {
            DefaultJavaExecSpec.this.executable(executable);
        }
    });

    private final Property<String> mainClass;
    private final Property<String> mainModule;
    private final ModularitySpec modularity;
    private final ListProperty<String> jvmArguments;

    private final FileCollectionFactory fileCollectionFactory;
    private ConfigurableFileCollection classpath;

    @Inject
    public DefaultJavaExecSpec(
        ObjectFactory objectFactory,
        PathToFileResolver resolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        super(objectFactory, resolver, fileCollectionFactory);
        this.jvmArguments = objectFactory.listProperty(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.mainModule = objectFactory.property(String.class);
        this.modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        this.fileCollectionFactory = fileCollectionFactory;
        this.classpath = fileCollectionFactory.configurableFiles("classpath");
        getIgnoreExitValue().convention(false);
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
    public Provider<List<String>> getCommandLine() {
        return getExecutable().map(executable -> argumentsSpec.getCommandLine());
    }

    @Override
    public JavaExecSpec args(Object... args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable List<String> arguments) {
        argumentsSpec.setArgs(arguments);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable Iterable<?> arguments) {
        argumentsSpec.setArgs(arguments);
        return this;
    }

    @Nullable
    @Override
    public List<String> getArgs() {
        return argumentsSpec.getArgs();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentsSpec.getArgumentProviders();
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
    public ListProperty<String> getJvmArguments() {
        return jvmArguments;
    }

    @Override
    public Property<String> getMainClass() {
        return mainClass;
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
