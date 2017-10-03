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
package org.gradle.configuration.project;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.ScriptApplicator;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildScriptProcessor implements ProjectConfigureAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildScriptProcessor.class);
    private final ScriptApplicator scriptApplicator;

    public BuildScriptProcessor(ScriptApplicator scriptApplicator) {
        this.scriptApplicator = scriptApplicator;
    }

    public void execute(ProjectInternal project) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Evaluating {} using {}.", project, project.getBuildScriptSource().getDisplayName());
        }
        final Timer clock = Time.startTimer();
        try {
            scriptApplicator.applyTo(project, project.getBuildScriptSource(), project.getBuildscript(), project.getClassLoaderScope(), project.getBaseClassLoaderScope(), true);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Timing: Running the build script took {}", clock.getElapsed());
            }
        }
    }
}
