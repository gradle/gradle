/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.configuration;

import org.gradle.api.GradleException;
import org.gradle.api.internal.StartParameterInternal;

import java.util.function.Consumer;

/**
 * An exception that when thrown leads to a restart.
 */
public class BuildRestartException extends GradleException {
    private final Consumer<StartParameterInternal> tweaker;

    public BuildRestartException(Consumer<StartParameterInternal> tweaker) {
        this.tweaker = tweaker;
    }

    public void customize(StartParameterInternal toCustomize) {
        tweaker.accept(toCustomize);
    }
}
