/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.VerificationTask;

import javax.inject.Inject;

/**
 * Validates task property annotations.
 *
 * <p>
 *     Task properties must be annotated with one of:
 * </p>
 *
 * <ul>
 *     <li>
 *         <b>Properties taken into account during up-to-date checks:</b>
 *         <ul>
 *              <li>
 *                 {@literal @}{@link org.gradle.api.tasks.Input},
 *                 {@literal @}{@link org.gradle.api.tasks.Nested},
 *                 {@literal @}{@link org.gradle.api.tasks.InputFile},
 *                 {@literal @}{@link org.gradle.api.tasks.InputDirectory},
 *                 {@literal @}{@link org.gradle.api.tasks.InputFiles}
 *                 to mark it as an input to the task.
 *             </li>
 *             <li>
 *                 {@literal @}{@link org.gradle.api.tasks.OutputFile},
 *                 {@literal @}{@link org.gradle.api.tasks.OutputDirectory}
 *                 to mark it as an output of the task.
 *             </li>
 *         </ul>
 *    </li>
 *    <li>
 *         <b>Properties ignored during up-to-date checks:</b>
 *         <ul>
 *             <li>{@literal @}{@link javax.inject.Inject} marks a Gradle service used by the task.</li>
 *             <li>{@literal @}{@link org.gradle.api.tasks.Console Console} marks a property that only influences the console output of the task.</li>
 *             <li>{@literal @}{@link org.gradle.api.tasks.Internal Internal} mark an internal property of the task.</li>
 *             <li>{@literal @}{@link org.gradle.api.model.ReplacedBy ReplacedBy} mark a property as replaced by another (similar to {@code Internal}).</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * @since 3.0
 *
 * @deprecated Use {@link ValidatePlugins} instead.
 */
@Deprecated
public class ValidateTaskProperties extends DefaultTask implements VerificationTask {
    private final TaskProvider<ValidatePlugins> delegate;
    private final Runnable deprecationNagger;

    @Inject
    public ValidateTaskProperties(TaskProvider<ValidatePlugins> delegate, Runnable deprecationNagger) {
        this.delegate = delegate;
        this.deprecationNagger = deprecationNagger;
    }

    @Internal
    @Override
    public boolean getIgnoreFailures() {
        deprecationNagger.run();
        return delegate.get().getIgnoreFailures().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        deprecationNagger.run();
        delegate.get().getIgnoreFailures().set(ignoreFailures);
    }

    /**
     * The classes to validate.
     *
     * @since 4.0
     */
    @Internal
    public ConfigurableFileCollection getClasses() {
        deprecationNagger.run();
        return delegate.get().getClasses();
    }

    /**
     * The classpath used to load the classes under validation.
     */
    @Internal
    public ConfigurableFileCollection getClasspath() {
        deprecationNagger.run();
        return delegate.get().getClasspath();
    }

    /**
     * Returns whether the build should break when the verifications performed by this task detects a warning.
     */
    @Internal
    public boolean getFailOnWarning() {
        deprecationNagger.run();
        return delegate.get().getFailOnWarning().get();
    }

    /**
     * Enable the stricter validation for cacheable tasks for all tasks.
     *
     * @since 5.1
     */
    @Internal
    public boolean getEnableStricterValidation() {
        deprecationNagger.run();
        return delegate.get().getEnableStricterValidation().get();
    }

    /**
     * Enable the stricter validation for cacheable tasks for all tasks.
     *
     * @since 5.1
     */
    public void setEnableStricterValidation(boolean enableStricterValidation) {
        deprecationNagger.run();
        delegate.get().getEnableStricterValidation().set(enableStricterValidation);
    }

    /**
     * Returns the output file to store the report in.
     *
     * @since 4.5
     */
    @Internal
    public RegularFileProperty getOutputFile() {
        deprecationNagger.run();
        return delegate.get().getOutputFile();
    }

    /**
     * Specifies whether the build should break when the verifications performed by this task detects a warning.
     *
     * @param failOnWarning {@code true} to break the build on warning, {@code false} to ignore warnings. The default is {@code true}.
     */
    public void setFailOnWarning(boolean failOnWarning) {
        deprecationNagger.run();
        delegate.get().getFailOnWarning().set(failOnWarning);
    }
}
