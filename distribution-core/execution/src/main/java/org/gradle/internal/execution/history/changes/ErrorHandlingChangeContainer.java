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

package org.gradle.internal.execution.history.changes;

import org.gradle.api.Describable;
import org.gradle.api.GradleException;

public class ErrorHandlingChangeContainer implements ChangeContainer {
    private final Describable executable;
    private final ChangeContainer delegate;

    public ErrorHandlingChangeContainer(Describable executable, ChangeContainer delegate) {
        this.executable = executable;
        this.delegate = delegate;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        try {
            return delegate.accept(visitor);
        } catch (Exception ex) {
            throw new GradleException(String.format("Cannot determine changes for %s", executable.getDisplayName()), ex);
        }
    }
}
