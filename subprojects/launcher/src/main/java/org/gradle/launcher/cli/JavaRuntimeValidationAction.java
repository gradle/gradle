/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.cli;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.util.GradleVersion;

public class JavaRuntimeValidationAction implements Action<ExecutionListener> {
    private final Action<? super ExecutionListener> action;

    public JavaRuntimeValidationAction(Action<? super ExecutionListener> action) {
        this.action = action;
    }

    public void execute(ExecutionListener executionListener) {
        JavaVersion javaVersion = JavaVersion.current();
        if (!javaVersion.isJava6Compatible()) {
            throw new GradleException(String.format("%s requires Java 6 or later to run. You are currently using Java %s.", GradleVersion.current(), javaVersion.getMajorVersion()));
        }
        action.execute(executionListener);
    }
}
