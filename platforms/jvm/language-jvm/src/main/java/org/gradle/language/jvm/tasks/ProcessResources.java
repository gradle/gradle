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
package org.gradle.language.jvm.tasks;

import org.gradle.api.tasks.Copy;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

/**
 * Copies resources from their source to their target directory, potentially processing them.
 * Makes sure no stale resources remain in the target directory.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class ProcessResources extends Copy {

    @Override
    protected void copy() {
        boolean cleanedOutputs = StaleOutputCleaner.cleanOutputs(getDeleter(), getOutputs().getPreviousOutputFiles(), getDestinationDir());
        super.copy();
        if (cleanedOutputs) {
            setDidWork(true);
        }
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }
}
