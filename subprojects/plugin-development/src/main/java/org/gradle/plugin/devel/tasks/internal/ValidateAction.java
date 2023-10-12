/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problem;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ValidateAction implements WorkAction<ValidateAction.Params> {
    private final static Logger LOGGER = Logging.getLogger(ValidateAction.class);
    public static final String PROBLEM_SEPARATOR = "--------";

    public interface Params extends WorkParameters {
        ConfigurableFileCollection getClasses();

        RegularFileProperty getOutputFile();

        Property<Boolean> getEnableStricterValidation();

        Property<ConfigurableFileCollection> getSourceSet();
    }

    @Override
    public void execute() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Params params = getParameters();

        ClassValidator classValidator = new ClassValidator(classLoader, params);
        params.getClasses().getAsFileTree().visit(classValidator);
        storeResults(classValidator.getTaskValidationProblems(), params.getOutputFile());
    }


    private static void storeResults(List<Problem> problemMessages, RegularFileProperty outputFile) {
        if (outputFile.isPresent()) {
            File output = outputFile.get().getAsFile();
            try {
                //noinspection ResultOfMethodCallIgnored
                output.createNewFile();
                Gson gson = ValidationProblemSerialization.createGsonBuilder().create();
                Files.asCharSink(output, Charsets.UTF_8).write(gson.toJson(problemMessages));
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }
    }

}
