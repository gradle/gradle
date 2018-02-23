/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations.logging;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GFileUtils;

import java.io.File;

public class DefaultBuildOperationLoggerFactory implements BuildOperationLoggerFactory {
    private static final int MAX_FAILURES = 10;

    private final Logger logger;

    DefaultBuildOperationLoggerFactory(Logger logger) {
        this.logger = logger;
    }

    public DefaultBuildOperationLoggerFactory() {
        this(Logging.getLogger(DefaultBuildOperationLoggerFactory.class));
    }


    @Override
    public BuildOperationLogger newOperationLogger(String taskName, File outputDir) {
        final File outputFile = createOutputFile(outputDir);
        final BuildOperationLogInfo configuration = createLogInfo(taskName, outputFile, MAX_FAILURES);
        return new DefaultBuildOperationLogger(configuration, logger, outputFile);
    }

    protected File createOutputFile(File outputDir) {
        GFileUtils.mkdirs(outputDir);
        return new File(outputDir, "output.txt");
    }

    protected BuildOperationLogInfo createLogInfo(String taskName, File outputFile, int maximumFailures) {
        final BuildOperationLogInfo configuration;
        if (logger.isDebugEnabled()) {
            // show all operation output when debug is enabled
            configuration = new BuildOperationLogInfo(taskName, outputFile, Integer.MAX_VALUE);
        } else {
            configuration = new BuildOperationLogInfo(taskName, outputFile, maximumFailures);
        }
        return configuration;
    }
}
