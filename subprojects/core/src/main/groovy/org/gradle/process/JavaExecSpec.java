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
package org.gradle.process;

import org.gradle.api.file.FileCollection;

import java.util.List;

/**
 * Specifies the options for executing a Java application.
 */
public interface JavaExecSpec extends JavaForkOptions, BaseExecSpec {
    /**
     * Returns the fully qualified name of the Main class to be executed.
     */
    String getMain();

    /**
     * Sets the fully qualified name of the main class to be executed.
     *
     * @param main the fully qualified name of the main class to be executed.
     *
     * @return this
     */
    JavaExecSpec setMain(String main);

    /**
     * Returns the arguments passed to the main class to be executed.
     */
    List<String> getArgs();

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec args(Object... args);

    /**
     * Adds args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec args(Iterable<?> args);

    /**
     * Sets the args for the main class to be executed.
     *
     * @param args Args for the main class.
     *
     * @return this
     */
    JavaExecSpec setArgs(Iterable<?> args);

    /**
     * Adds elements to the classpath for executing the main class.
     *
     * @param paths classpath elements
     *
     * @return this
     */
    JavaExecSpec classpath(Object... paths);

    /**
     * Returns the classpath for executing the main class.
     */
    FileCollection getClasspath();

    /**
     * Sets the classpath for executing the main class.
     *
     * @param classpath the classpath
     *
     * @return this
     */
    JavaExecSpec setClasspath(FileCollection classpath);
}
