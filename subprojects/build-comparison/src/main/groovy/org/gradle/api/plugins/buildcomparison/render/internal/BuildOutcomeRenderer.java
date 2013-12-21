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

package org.gradle.api.plugins.buildcomparison.render.internal;

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;

/**
 * An object that can render a build outcome.
 *
 * @param <T> The type of outcome that this renderer handles
 * @param <C> The type of the context object for the render
 */
public interface BuildOutcomeRenderer<T extends BuildOutcome, C> {

    Class<T> getOutcomeType();

    Class<C> getContextType();

    void render(T outcome, C context);

}
