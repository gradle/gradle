/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Process execution operations.
 *
 * <p>An instance of this type can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 *
 * @since 6.0
 */
@ServiceScope(Scopes.Build.class)
public interface ExecOperations {

    /**
     * Executes the specified external process.
     * The given action is used to configure an {@link ExecSpec}, which is then used to run an external process.
     *
     * @param action Action to configure the ExecSpec
     * @return {@link ExecResult} that can be used to check if the execution worked
     */
    ExecResult exec(Action<? super ExecSpec> action);

    /**
     * Executes the specified external <code>java</code> process.
     * The given action is used to configure an {@link JavaExecSpec}, which is then used to run an external <code>java</code> process.
     *
     * @param action Action to configure the JavaExecSpec
     * @return {@link ExecResult} that can be used to check if the execution worked
     */
    ExecResult javaexec(Action<? super JavaExecSpec> action);
}
