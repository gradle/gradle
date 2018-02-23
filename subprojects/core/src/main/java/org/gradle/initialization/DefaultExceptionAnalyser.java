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
package org.gradle.initialization;

import org.gradle.api.GradleScriptException;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.Script;
import org.gradle.groovy.scripts.ScriptCompilationException;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;

import java.util.HashMap;
import java.util.Map;

public class DefaultExceptionAnalyser implements ExceptionAnalyser, ScriptExecutionListener {
    private final Map<String, ScriptSource> scripts = new HashMap<String, ScriptSource>();

    public DefaultExceptionAnalyser(ListenerManager listenerManager) {
        listenerManager.addListener(this);
    }

    @Override
    public void scriptClassLoaded(ScriptSource source, Class<? extends Script> scriptClass) {
        scripts.put(source.getFileName(), source);
    }

    public Throwable transform(Throwable exception) {
        Throwable actualException = findDeepestRootException(exception);
        if (actualException instanceof LocationAwareException) {
            return actualException;
        }

        ScriptSource source = null;
        Integer lineNumber = null;

        // TODO: remove these special cases
        if (actualException instanceof ScriptCompilationException) {
            ScriptCompilationException scriptCompilationException = (ScriptCompilationException) actualException;
            source = scriptCompilationException.getScriptSource();
            lineNumber = scriptCompilationException.getLineNumber();
        }

        if (source == null) {
            for (
                    Throwable currentException = actualException; currentException != null;
                    currentException = currentException.getCause()) {
                for (StackTraceElement element : currentException.getStackTrace()) {
                    if (element.getLineNumber() >= 0 && scripts.containsKey(element.getFileName())) {
                        source = scripts.get(element.getFileName());
                        lineNumber = element.getLineNumber();
                        break;
                    }
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
