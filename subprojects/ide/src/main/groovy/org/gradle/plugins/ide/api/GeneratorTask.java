/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.api;

import org.gradle.api.GradleException;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.internal.generator.generator.Generator;

import javax.inject.Inject;
import java.io.File;

/**
 * <p>A {@code GeneratorTask} generates a configuration file based on a domain object of type T.
 * When executed the task:
 * <ul>
 *
 * <li>loads the object from the input file, if it exists.</li>
 *
 * <li>Calls the beforeConfigured actions, passing the object to each action.</li>
 *
 * <li>Configures the object in some task-specific way.</li>
 *
 * <li>Calls the afterConfigured actions, passing the object to each action.</li>
 *
 * <li>writes the object to the output file.</li>
 *
 * </ul>
 *
 * @param <T> The domain object for the configuration file.
 */
public class GeneratorTask<T> extends ConventionTask {
    private File inputFile;
    private File outputFile;
    protected final ActionBroadcast<T> beforeConfigured = new ActionBroadcast<T>();
    protected final ActionBroadcast<T> afterConfigured = new ActionBroadcast<T>();
    protected Generator<T> generator;

    protected T domainObject;

    public GeneratorTask() {
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    @SuppressWarnings("UnusedDeclaration")
    @TaskAction
    void generate() {
        File inputFile = getInputFile();
        if (inputFile != null && inputFile.exists()) {
            try {
                domainObject = generator.read(inputFile);
            } catch (RuntimeException e) {
                throw new GradleException(String.format("Cannot parse file '%s'.\n"
                        + "       Perhaps this file was tinkered with? In that case try delete this file and then retry.",
                        inputFile), e);
            }
        } else {
            domainObject = generator.defaultInstance();
        }
        beforeConfigured.execute(domainObject);
        generator.configure(domainObject);
        afterConfigured.execute(domainObject);

        generator.write(domainObject, getOutputFile());
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    /**
     * The input file to load the initial configuration from. Defaults to the output file. If the specified input file
     * does not exist, this task uses some default initial configuration.
     *
     * @return The input file.
     */
    public File getInputFile() {
        return inputFile != null ? inputFile : getOutputFile();
    }

    /**
     * Sets the input file to load the initial configuration from.
     *
     * @param inputFile The input file. Use null to use the output file.
     */
    public void setInputFile(File inputFile) {
        this.inputFile = inputFile;
    }

    /**
     * The output file to write the final configuration to.
     *
     * @return The output file.
     */
    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the output file to write the final configuration to.
     *
     * @param outputFile The output file.
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

}
