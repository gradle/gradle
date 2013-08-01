/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

class DefaultBuildActionExecuter<T> implements BuildActionExecuter<T> {
    private final BuildAction<T> buildAction;

    public DefaultBuildActionExecuter(BuildAction<T> buildAction) {
        this.buildAction = buildAction;
    }

    public T run() throws GradleConnectionException {
        return buildAction.execute(new BuildController() {
        });
    }

    public void run(ResultHandler<? super T> handler) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation setStandardOutput(OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation setStandardError(OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation setStandardInput(InputStream inputStream) {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation setJavaHome(File javaHome) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation setJvmArguments(String... jvmArguments) {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation withArguments(String... arguments) {
        throw new UnsupportedOperationException();
    }

    public LongRunningOperation addProgressListener(ProgressListener listener) {
        throw new UnsupportedOperationException();
    }
}
