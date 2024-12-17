/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Configures exec handle builders from spec objects that live in core-api.
 *
 * This logic can't live as default methods on the builder interfaces because those interfaces
 * are in process-services-base which doesn't depend on core-api where the spec types live.
 */
@NullMarked
public class ExecHandleBuilderConfigurer {

    public static <T extends BaseExecHandleBuilder> T configureFrom(T builder, BaseExecSpec spec) {
        if (spec.getStandardInput().isPresent()) {
            builder.setStandardInput(spec.getStandardInput().get());
        }
        if (spec.getStandardOutput().isPresent()) {
            builder.setStandardOutput(spec.getStandardOutput().get());
        }
        if (spec.getErrorOutput().isPresent()) {
            builder.setErrorOutput(spec.getErrorOutput().get());
        }
        if (spec.getExecutable().isPresent()) {
            builder.setExecutable(spec.getExecutable().get());
        }
        if (spec.getWorkingDir().isPresent()) {
            builder.setWorkingDir(spec.getWorkingDir().get().getAsFile());
        }
        builder.setEnvironment(spec.getEnvironment().get());
        return builder;
    }

    public static ClientExecHandleBuilder configureFrom(ClientExecHandleBuilder builder, ExecSpec execSpec) {
        configureFrom(builder, (BaseExecSpec) execSpec);
        builder.setArgs(execSpec.getArgs().get());
        builder.setArgumentProviders(execSpec.getArgumentProviders().get());
        return builder;
    }
}
