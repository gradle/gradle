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
package org.gradle.internal.buildevents;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.time.Clock;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Success;

/**
 * A {@link org.gradle.BuildListener} which logs the final result of the build.
 */
public class BuildResultLogger extends BuildAdapter {
    private final StyledTextOutputFactory textOutputFactory;
    private final Clock buildTimeClock;

    public BuildResultLogger(StyledTextOutputFactory textOutputFactory, Clock buildTimeClock) {
        this.textOutputFactory = textOutputFactory;
        this.buildTimeClock = buildTimeClock;
    }

    public void buildFinished(BuildResult result) {
        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
        textOutput.println();
        String action = result.getAction().toUpperCase();
        if (result.getFailure() == null) {
            textOutput.withStyle(Success).text(action + " SUCCESSFUL");
        } else {
            textOutput.withStyle(Failure).text(action + " FAILED");
        }
        textOutput.println();
        textOutput.println();
        textOutput.formatln("Total time: %s", buildTimeClock.getElapsed());
    }
}
