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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

/**
 * The factory to build appropriate ExecSpec and JavaExecSpec instances. The returned
 * instances, especially JavaExecSpec, must conform to the {@link BaseExecSpec#getCommandLine()} contract.
 *
 * These instances will not be exposed to the user code directly, so there is no need of decoration.
 */
@ServiceScope(Scopes.Build.class)
public interface ExecSpecFactory {
    ExecSpec newExecSpec();

    JavaExecSpec newJavaExecSpec();
}
