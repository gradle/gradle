/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks;

import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultExecSpec;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * {@code AbstractExecTask} is the base class for all exec tasks.
 *
 * @param <T> The concrete type of the class.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractExecTask<T extends AbstractExecTask> extends ConventionTask implements ExecSpec {

    private final Class<T> taskType;
    private final Property<ExecResult> execResult;
    private final DefaultExecSpec execSpec;

    public AbstractExecTask(Class<T> taskType) {
        execSpec = getObjectFactory().newInstance(DefaultExecSpec.class);
        execResult = getObjectFactory().property(ExecResult.class);
        this.taskType = taskType;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void exec() {
        ExecAction execAction = getExecActionFactory().newExecAction();
        execSpec.copyTo(execAction);
        execResult.set(execAction.execute());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T commandLine(Object... arguments) {
        execSpec.commandLine(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T commandLine(Iterable<?> args) {
        execSpec.commandLine(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T args(Object... args) {
        execSpec.args(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T args(Iterable<?> args) {
        execSpec.args(args);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T setArgs(List<String> arguments) {
        execSpec.setArgs(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T setArgs(@Nullable Iterable<?> arguments) {
        execSpec.setArgs(arguments);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Optional
    @Input
    @Override
    @ToBeReplacedByLazyProperty(unreported = true, comment = "Unreported since setter is using generics")
    public List<String> getArgs() {
        return execSpec.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Nested
    @Override
    @ToBeReplacedByLazyProperty
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return execSpec.getArgumentProviders();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ToBeReplacedByLazyProperty
    public List<String> getCommandLine() {
        return execSpec.getCommandLine();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandLine(List<String> args) {
        execSpec.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandLine(Iterable<?> args) {
        execSpec.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandLine(Object... args) {
        execSpec.setCommandLine(args);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Optional
    @Input
    @Override
    @ToBeReplacedByLazyProperty
    public String getExecutable() {
        return execSpec.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(@Nullable String executable) {
        execSpec.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(Object executable) {
        execSpec.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T executable(Object executable) {
        execSpec.executable(executable);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @ToBeReplacedByLazyProperty
    // TODO:LPTR Should be a content-less @InputDirectory
    public File getWorkingDir() {
        return execSpec.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(File dir) {
        execSpec.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(Object dir) {
        execSpec.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T workingDir(Object dir) {
        execSpec.workingDir(dir);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ToBeReplacedByLazyProperty
    public Map<String, Object> getEnvironment() {
        return execSpec.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        execSpec.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T environment(String name, Object value) {
        execSpec.environment(name, value);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T environment(Map<String, ?> environmentVariables) {
        execSpec.environment(environmentVariables);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T copyTo(ProcessForkOptions target) {
        execSpec.copyTo(target);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ReplacesEagerProperty(adapter = StandardInputAdapter.class)
    public Property<InputStream> getStandardInput() {
        return execSpec.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ReplacesEagerProperty(adapter = StandardOutputAdapter.class)
    public Property<OutputStream> getStandardOutput() {
        return execSpec.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ReplacesEagerProperty(adapter = ErrorOutputAdapter.class)
    public Property<OutputStream> getErrorOutput() {
        return execSpec.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Input
    @Override
    @ReplacesEagerProperty(adapter = IgnoreExitValueAdapter.class)
    public Property<Boolean> getIgnoreExitValue() {
        return execSpec.getIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @Deprecated
    public Property<Boolean> getIsIgnoreExitValue() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsIgnoreExitValue()", "getIgnoreExitValue()");
        return getIgnoreExitValue();
    }

    /**
     * Returns the result for the command run by this task. The provider has no value if this task has not been executed yet.
     *
     * @return A provider of the result.
     * @since 6.1
     */
    @Internal
    public Provider<ExecResult> getExecutionResult() {
        return execResult;
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class IgnoreExitValueAdapter {
        @SuppressWarnings("rawtypes")
        @BytecodeUpgrade
        static AbstractExecTask setIgnoreExitValue(AbstractExecTask task, boolean value) {
            ((BaseExecSpec) task).getIgnoreExitValue().set(value);
            return task;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class StandardInputAdapter {
        @SuppressWarnings({"rawtypes", "unchecked"})
        @BytecodeUpgrade
        static AbstractExecTask setStandardInput(AbstractExecTask task, InputStream inputStream) {
            task.getStandardInput().set(inputStream);
            return task;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class StandardOutputAdapter {
        @SuppressWarnings({"rawtypes", "unchecked"})
        @BytecodeUpgrade
        static AbstractExecTask setStandardOutput(AbstractExecTask task, OutputStream outputStream) {
            task.getStandardOutput().set(outputStream);
            return task;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class ErrorOutputAdapter {
        @SuppressWarnings({"rawtypes", "unchecked"})
        @BytecodeUpgrade
        static AbstractExecTask setErrorOutput(AbstractExecTask task, OutputStream outputStream) {
            task.getErrorOutput().set(outputStream);
            return task;
        }
    }
}
