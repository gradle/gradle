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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.process.ExecResult;

import java.util.concurrent.Executor;

/**
 * Use {@link ExecActionFactory} or {@link DslExecActionFactory} instead.
 */
public class DefaultJavaExecAction extends JavaExecHandleBuilder implements JavaExecAction {
    public DefaultJavaExecAction(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Executor executor, BuildCancellationToken buildCancellationToken) {
        super(fileResolver, fileCollectionFactory, executor, buildCancellationToken);
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!isIgnoreExitValue()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }
}
