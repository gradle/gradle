/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.internal.jvm.Jvm;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public abstract class AntWorkAction<T extends AntWorkParameters> implements WorkAction<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntWorkAction.class);

    @Override
    public void execute() {
        LOGGER.info("Running {} with toolchain '{}'.", getActionName(), Jvm.current().getJavaHome().getAbsolutePath());

        getIsolatedAntBuilder()
            .withClasspath(getParameters().getAntLibraryClasspath())
            .execute(getAntAction());
    }

    protected abstract String getActionName();

    protected abstract Action<AntBuilderDelegate> getAntAction();

    @Inject
    protected abstract IsolatedAntBuilder getIsolatedAntBuilder();
}
