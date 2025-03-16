/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.internal.ConfigureUtil;
import org.jspecify.annotations.Nullable;

public class DeprecatedProcessOperations {
    @Nullable
    private final Class<?> owner;
    private final ProcessOperations processOperations;

    public DeprecatedProcessOperations(Class<?> owner, ProcessOperations processOperations) {
        this.owner = owner;
        this.processOperations = processOperations;
    }

    public DeprecatedProcessOperations(ProcessOperations processOperations) {
        this.owner = null;
        this.processOperations = processOperations;
    }

    /**
     * Emits deprecation warning and runs {@link ProcessOperations#javaexec(Action)}.
     */
    public ExecResult javaexec(Closure<?> closure) {
        nagUser("javaexec(Closure)", "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)");
        return processOperations.javaexec(ConfigureUtil.configureUsing(closure));
    }

    private void nagUser(String method, String replacement) {
        DeprecationMessageBuilder.WithReplacement<String, ?> builder = owner != null ? DeprecationLogger.deprecateMethod(owner, method) : DeprecationLogger.deprecateInvocation(method);
        builder.withAdvice("Use " + replacement + " instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_project_exec")
            .nagUser();
    }

    /**
     * Emits deprecation warning and runs {@link ProcessOperations#javaexec(Action)}.
     */
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        nagUser("javaexec(Action)", "ExecOperations.javaexec(Action) or ProviderFactory.javaexec(Action)");
        return processOperations.javaexec(action);
    }

    /**
     * Emits deprecation warning and runs {@link ProcessOperations#exec(Action)}.
     */
    public ExecResult exec(Closure<?> closure) {
        nagUser("exec(Closure)", "ExecOperations.exec(Action) or ProviderFactory.exec(Action)");
        return processOperations.exec(ConfigureUtil.configureUsing(closure));
    }

    /**
     * Emits deprecation warning and runs {@link ProcessOperations#exec(Action)}.
     */
    public ExecResult exec(Action<? super ExecSpec> action) {
        nagUser("exec(Action)", "ExecOperations.exec(Action) or ProviderFactory.exec(Action)");
        return processOperations.exec(action);
    }
}
