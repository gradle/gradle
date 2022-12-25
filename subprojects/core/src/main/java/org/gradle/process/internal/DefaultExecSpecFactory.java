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

package org.gradle.process.internal;

import org.gradle.api.internal.provider.sources.process.ExecSpecFactory;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

public class DefaultExecSpecFactory implements ExecSpecFactory {
    private final ExecActionFactory execActionFactory;

    public DefaultExecSpecFactory(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    @Override
    public ExecSpec newExecSpec() {
        return execActionFactory.newExecAction();
    }

    @Override
    public JavaExecSpec newJavaExecSpec() {
        return execActionFactory.newJavaExecAction();
    }
}
