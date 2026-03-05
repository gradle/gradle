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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Executes a command line process. Example:
 * <pre class='autoTested'>
 * task stopTomcat(type:Exec) {
 *   workingDir '../tomcat/bin'
 *
 *   //on windows:
 *   commandLine 'cmd.exe', '/d', '/c', 'stop.bat'
 *
 *   //on linux
 *   commandLine './stop.sh'
 *
 *   //store the output instead of printing to the console:
 *   standardOutput = new ByteArrayOutputStream()
 *
 *   //extension method stopTomcat.output() can be used to obtain the output:
 *   ext.output = {
 *     return standardOutput.toString()
 *   }
 * }
 * </pre>
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public abstract class Exec extends AbstractExecTask<Exec> {
    public Exec() {
        super(Exec.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacesEagerProperty(adapter = Exec.ArgsAdapter.class)
    public ListProperty<String> getArgs() {
        return super.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacesEagerProperty(adapter = Exec.IgnoreExitValueAdapter.class)
    public Property<Boolean> getIgnoreExitValue() {
        return super.getIgnoreExitValue();
    }
    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacesEagerProperty(adapter = Exec.StandardInputAdapter.class)
    public Property<InputStream> getStandardInput() {
        return super.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ReplacesEagerProperty(adapter = Exec.StandardOutputAdapter.class)
    public Property<OutputStream> getStandardOutput() {
        return super.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacesEagerProperty(adapter = Exec.ErrorOutputAdapter.class)
    public Property<OutputStream> getErrorOutput() {
        return super.getErrorOutput();
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class IgnoreExitValueAdapter {
        @BytecodeUpgrade
        static Exec setIgnoreExitValue(Exec self, boolean value) {
            self.getIgnoreExitValue().set(value);
            return self;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via ExecSpec
     */
    static class ArgsAdapter {
        @BytecodeUpgrade
        static Exec setArgs(Exec self, List<String> args) {
            return setArgs(self, (Iterable<?>) args);
        }

        @BytecodeUpgrade
        static Exec setArgs(Exec self, Iterable<?> args) {
            AbstractExecTask.ArgsAdapter.setArgs(self, args);
            return self;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class StandardInputAdapter {
        @BytecodeUpgrade
        static Exec setStandardInput(Exec self, InputStream value) {
            self.getStandardInput().set(value);
            return self;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class StandardOutputAdapter {
        @BytecodeUpgrade
        static Exec setStandardOutput(Exec self, OutputStream value) {
            self.getStandardOutput().set(value);
            return self;
        }
    }

    /**
     * No need to upgrade getter since it's already upgraded via BaseExecSpec
     */
    static class ErrorOutputAdapter {
        @BytecodeUpgrade
        static Exec setErrorOutput(Exec self, OutputStream value) {
            self.getErrorOutput().set(value);
            return self;
        }
    }
}
