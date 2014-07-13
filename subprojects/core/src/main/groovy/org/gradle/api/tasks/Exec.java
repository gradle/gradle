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

import org.gradle.process.ProcessForkOptions;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Executes a command line process. Example:
 * <pre autoTested=''>
 * task stopTomcat(type:Exec) {
 *   workingDir '../tomcat/bin'
 *
 *   //on windows:
 *   commandLine 'cmd', '/c', 'stop.bat'
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
public class Exec extends AbstractExecTask {
    /**
     * {@inheritDoc}
     */
    public Exec commandLine(Object... arguments) {
        super.commandLine(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec args(Object... args) {
        super.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setArgs(Iterable<?> arguments) {
        super.setArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec executable(Object executable) {
        super.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec workingDir(Object dir) {
        super.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec environment(String name, Object value) {
        super.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec environment(Map<String, ?> environmentVariables) {
        super.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec copyTo(ProcessForkOptions target) {
        super.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setStandardInput(InputStream inputStream) {
        super.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Exec setErrorOutput(OutputStream outputStream) {
        super.setErrorOutput(outputStream);
        return this;
    }
}
