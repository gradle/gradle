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

import java.util.HashMap;
import java.util.Map;

public class DefaultBuildOutcomeRendererFactory<C> implements BuildOutcomeRendererFactory<C> {

    private final Class<C> contextType;

    private final Map<Class<? extends BuildOutcome>, BuildOutcomeRenderer<?, C>> renderers =
            new HashMap<Class<? extends BuildOutcome>, BuildOutcomeRenderer<?, C>>();

    public DefaultBuildOutcomeRendererFactory(Class<C> contextType) {
        this.contextType = contextType;
    }

    public <T extends BuildOutcome> void registerRenderer(BuildOutcomeRenderer<T, C> renderer) {
        if (renderer.getContextType().isAssignableFrom(contextType)) {
            renderers.put(renderer.getOutcomeType(), renderer);
        } else {
            throw new IllegalArgumentException(
                    String.format("Renderer '%s' has context type '%s' which is incompatible with target context type '%s'", renderer, renderer.getContextType(), contextType)
            );
        }
    }

    public <T extends BuildOutcome> BuildOutcomeRenderer<T, C> getRenderer(Class<? extends T> resultType) {
        //noinspection unchecked
        return (BuildOutcomeRenderer<T, C>) renderers.get(resultType);
    }

}
