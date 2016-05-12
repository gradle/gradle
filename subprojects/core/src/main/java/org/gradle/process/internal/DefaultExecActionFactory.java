/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.file.FileResolver;

public class DefaultExecActionFactory implements ExecActionFactory, ExecHandleFactory, JavaExecHandleFactory {
    private final FileResolver fileResolver;

    public DefaultExecActionFactory(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public ExecAction newExecAction() {
        return new DefaultExecAction(fileResolver);
    }

    @Override
    public JavaExecAction newJavaExecAction() {
        return new DefaultJavaExecAction(fileResolver);
    }

    @Override
    public ExecHandleBuilder newExec() {
        return new DefaultExecHandleBuilder(fileResolver);
    }

    @Override
    public JavaExecHandleBuilder newJavaExec() {
        return new JavaExecHandleBuilder(fileResolver);
    }
}
