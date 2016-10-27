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

package org.gradle.performance.fixture;

import org.apache.commons.io.FileUtils;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;

public class LogFiles {
    public static File createTempLogFile(String prefix, String postfix) {
        try {
            // see comment in org.gradle.integtests.fixtures.executer.InProcessGradleExecuter.getDefaultTmpDir() to find out why directory is passed explicitly
            return File.createTempFile(prefix, postfix, new File(System.getProperty("java.io.tmpdir")));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static void copyLogFile(File logFile, BuildExperimentInvocationInfo invocationInfo, String prefix, String postfix) {
        String fileName = createFileNameForBuildInvocation(invocationInfo, prefix, postfix);
        try {
            FileUtils.copyFile(logFile, new File(invocationInfo.getProjectDir(), fileName));
        } catch (IOException e) {
            // ignore
        }
    }

    public static String createFileNameForBuildInvocation(BuildExperimentInvocationInfo invocationInfo, String prefix, String postfix) {
        return (prefix + invocationInfo.getBuildExperimentSpec().getDisplayName() + "_"
            + invocationInfo.getBuildExperimentSpec().getProjectName() + "_"
            + invocationInfo.getPhase().name().toLowerCase() + "_"
            + invocationInfo.getIterationNumber() + "_"
            + invocationInfo.getIterationMax() + postfix).replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("[_]+", "_");
    }
}
