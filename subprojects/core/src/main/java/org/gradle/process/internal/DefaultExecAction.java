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

package org.gradle.process.internal;

import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.ExecResult;

import java.util.concurrent.Executor;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 */
public class DefaultExecAction extends DefaultExecHandleBuilder implements ExecAction {
    public DefaultExecAction(ObjectFactory objectFactory, PathToFileResolver fileResolver, Executor executor, BuildCancellationToken buildCancellationToken) {
        super(objectFactory, fileResolver, executor, buildCancellationToken);
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }
}
