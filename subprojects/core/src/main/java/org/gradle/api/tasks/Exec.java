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
    public ListProperty<String> getArgs() {
        return super.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setArgs(List<String> args) {
        return setArgs((Iterable<?>) args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setArgs(Iterable<?> args) {
        getArgs().empty();
        args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Property<Boolean> getIgnoreExitValue() {
        return super.getIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setIgnoreExitValue(boolean value) {
        getIgnoreExitValue().set(value);
        return this;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public Property<InputStream> getStandardInput() {
        return super.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setStandardInput(InputStream value) {
        getStandardInput().set(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    public Property<OutputStream> getStandardOutput() {
        return super.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setStandardOutput(OutputStream value) {
        getStandardOutput().set(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Property<OutputStream> getErrorOutput() {
        return super.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setErrorOutput(OutputStream value) {
        getErrorOutput().set(value);
        return this;
    }

}
