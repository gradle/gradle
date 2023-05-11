/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.initialization.exception;

import org.gradle.api.GradleScriptException;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.ScriptCompilationException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.service.ServiceCreationException;
import org.gradle.problems.Location;
import org.gradle.problems.buildtree.ProblemLocationAnalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DefaultExceptionAnalyser implements ExceptionCollector {
    private final ProblemLocationAnalyzer locationAnalyzer;

    public DefaultExceptionAnalyser(ProblemLocationAnalyzer locationAnalyzer) {
        this.locationAnalyzer = locationAnalyzer;
    }

    @Override
    public void collectFailures(Throwable exception, Collection<? super Throwable> failures) {
        if (exception instanceof ProjectConfigurationException) {
            ProjectConfigurationException projectConfigurationException = (ProjectConfigurationException) exception;
            List<Throwable> additionalFailures = new ArrayList<>();
            for (Throwable cause : projectConfigurationException.getCauses()) {
                // TODO: remove this special case
                if (cause instanceof GradleScriptException) {
                    failures.add(transform(cause));
                } else {
                    additionalFailures.add(cause);
                }
            }
            if (!additionalFailures.isEmpty()) {
                projectConfigurationException.initCauses(additionalFailures);
                failures.add(transform(projectConfigurationException));
            }
        } else if (exception instanceof ServiceCreationException) {
            failures.add(transform(new InitializationException(exception)));
        } else {
            failures.add(transform(exception));
        }
    }

    private Throwable transform(Throwable exception) {
        Throwable actualException = findDeepestRootException(exception);
        if (actualException instanceof LocationAwareException) {
            return actualException;
        }

        String source = null;
        Integer lineNumber = null;

        // TODO: remove these special cases
        if (actualException instanceof ScriptCompilationException) {
            ScriptCompilationException scriptCompilationException = (ScriptCompilationException) actualException;
            source = scriptCompilationException.getScriptSource().getLongDisplayName().getCapitalizedDisplayName();
            lineNumber = scriptCompilationException.getLineNumber();
        }

        if (source == null) {
            for (
                Throwable currentException = actualException;
                currentException != null;
                currentException = currentException.getCause()
            ) {
                Location location = locationAnalyzer.locationForUsage(Arrays.asList(currentException.getStackTrace()), true);
                if (location != null) {
                    source = location.getSourceLongDisplayName().getCapitalizedDisplayName();
                    lineNumber = location.getLineNumber();
                }
            }
        }

        return new LocationAwareException(actualException, source, lineNumber);
    }

    private Throwable findDeepestRootException(Throwable exception) {
        // TODO: fix the way we work out which exception is important: TaskExecutionException is not always the most helpful
        Throwable locationAware = null;
        Throwable result = null;
        Throwable contextMatch = null;
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof LocationAwareException) {
                locationAware = current;
            } else if (current instanceof GradleScriptException || current instanceof TaskExecutionException) {
                result = current;
            } else if (contextMatch == null && current.getClass().getAnnotation(Contextual.class) != null) {
                contextMatch = current;
            }
        }
        if (locationAware != null) {
            return locationAware;
        } else if (result != null) {
            return result;
        } else if (contextMatch != null) {
            return contextMatch;
        } else {
            return exception;
        }
    }
}
