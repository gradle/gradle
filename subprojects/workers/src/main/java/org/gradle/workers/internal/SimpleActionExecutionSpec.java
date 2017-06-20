/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import java.io.File;

public class SimpleActionExecutionSpec implements ActionExecutionSpec {
    private final Class<? extends Runnable> implementationClass;
    private final String displayName;
    private final File executionWorkingDir;
    private final Object[] params;

    public SimpleActionExecutionSpec(Class<? extends Runnable> implementationClass, String displayName, File executionWorkingDir, Object[] params) {
        this.implementationClass = implementationClass;
        this.displayName = displayName;
        this.executionWorkingDir = executionWorkingDir;
        this.params = params;
    }

    @Override
    public Class<? extends Runnable> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public File getExecutionWorkingDir() {
        return executionWorkingDir;
    }

    @Override
    public Object[] getParams(ClassLoader classLoader) {
        return params;
    }
}
