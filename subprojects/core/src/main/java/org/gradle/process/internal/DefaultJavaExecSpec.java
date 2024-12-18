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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.JavaExecSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;

import static org.gradle.process.internal.DefaultExecSpec.copyBaseExecSpecTo;


public abstract class DefaultJavaExecSpec extends DefaultJavaForkOptions implements JavaExecSpec {

    private final Property<String> mainClass;
    private final Property<String> mainModule;
    private final ModularitySpec modularity;
    private final ListProperty<String> jvmArguments;

    /**
     * In scopes JavaModuleDetector is not available, e.g. in Process isolation Worker. In such cases, this field will be null.
     */
    @Nullable
    private final JavaModuleDetector javaModuleDetector;

    @Inject
    public DefaultJavaExecSpec(
        ObjectFactory objectFactory,
        PathToFileResolver resolver,
        FileCollectionFactory fileCollectionFactory,
        Factory<JavaModuleDetector> javaModuleDetector
    ) {
        super(objectFactory, resolver, fileCollectionFactory);
        this.jvmArguments = objectFactory.listProperty(String.class);
        this.mainClass = objectFactory.property(String.class);
        this.mainModule = objectFactory.property(String.class);
        this.modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        this.javaModuleDetector = javaModuleDetector.create();
        getIgnoreExitValue().convention(false);
    }

    public void copyTo(JavaExecSpec targetSpec) {
        // JavaExecSpec
        targetSpec.getArgs().set(getArgs());
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
        return getExecutable().zip(getAllJvmArgs(), (SerializableLambdas.SerializableBiFunction<String, List<String>, List<String>>) (executable, allJvmArgs) -> {
            List<String> allArgs = ExecHandleCommandLineCombiner.getAllArgs(allJvmArgs, getArgs().get(), getArgumentProviders().get());
            return ExecHandleCommandLineCombiner.getCommandLine(executable, allArgs);
        });
    }

    @Override
    public JavaExecSpec args(Object... args) {
        DefaultExecSpec.collectArgs(getArgs(), args);
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        for (Object arg : args) {
            args(arg);
        }
        return this;
    }

    @Override
    public JavaExecSpec classpath(Object... paths) {
        getClasspath().from(paths);
        return this;
    }

    @Override
    public Provider<List<String>> getAllJvmArgs() {
        return super.getAllJvmArgs().map((SerializableLambdas.SerializableTransformer<List<String>, List<String>>) bootstrapJvmArgs -> ExecHandleCommandLineCombiner.getAllJvmArgs(
            bootstrapJvmArgs,
            getClasspath(),
            getMainClass(),
            getMainModule(),
            getModularity(),
            javaModuleDetector
        ));
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
