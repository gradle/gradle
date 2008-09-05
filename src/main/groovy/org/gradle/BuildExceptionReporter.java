/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle;

import org.slf4j.Logger;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.api.GradleException;
import joptsimple.OptionSet;

import java.util.Formatter;

/**
 * A {@link BuildListener} which reports the build exception, if any.
 */
public class BuildExceptionReporter implements BuildListener {
    private final Logger logger;
    private OptionSet options;

    public BuildExceptionReporter(Logger logger) {
        this.logger = logger;
    }

    public void setOptions(OptionSet options) {
        this.options = options;
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        if (failure instanceof GradleException) {
            reportBuildFailure(failure);
        } else {
            reportInternalError(failure);
        }
    }

    public void reportInternalError(Throwable failure) {
        Formatter formatter = new Formatter();
        formatter.format("%n");
        formatter.format("Build aborted because of an internal error. Run with -%s option to get additonal debug info. Please file an issue at: www.gradle.org", Main.DEBUG);
        logger.error(formatter.toString(), failure);
    }

    private void reportBuildFailure(Throwable failure) {
        boolean stacktrace = options != null && (options.has(Main.STACKTRACE) || options.has(Main.DEBUG));
        boolean fullStacktrace = options != null && (options.has(Main.FULL_STACKTRACE));
        Formatter formatter = new Formatter();
        formatter.format("%n");
        formatter.format("Build failed with an exception. ");
        if (!stacktrace && !fullStacktrace) {
            formatter.format(" Run with -%s or -%s option to get stacktrace.", Main.STACKTRACE, Main.DEBUG);
        }
        if (!fullStacktrace) {
            formatter.format(" Run with -%s option to get the full (very verbose) stacktrace.", Main.FULL_STACKTRACE);
        }
        logger.error(formatter.toString());
        if (stacktrace || fullStacktrace) {
            logger.error("Exception is:", fullStacktrace ? failure : StackTraceUtils.deepSanitize(failure));
        } else {
            logger.error("Exception: " + failure);
        }
    }
}
