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

import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
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
    @Nullable
    @ToBeReplacedByLazyProperty
    public List<String> getArgs() {
        return super.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setArgs(List<String> arguments) {
        return super.setArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setArgs(@Nullable Iterable<?> arguments) {
        return super.setArgs(arguments);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
    public boolean isIgnoreExitValue() {
        return super.isIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setIgnoreExitValue(boolean ignoreExitValue) {
        return super.setIgnoreExitValue(ignoreExitValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
    public InputStream getStandardInput() {
        return super.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setStandardInput(InputStream inputStream) {
        return super.setStandardInput(inputStream);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ToBeReplacedByLazyProperty
    public OutputStream getStandardOutput() {
        return super.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setStandardOutput(OutputStream outputStream) {
        return super.setStandardOutput(outputStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
    public OutputStream getErrorOutput() {
        return super.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Exec setErrorOutput(OutputStream outputStream) {
        return super.setErrorOutput(outputStream);
    }
}
