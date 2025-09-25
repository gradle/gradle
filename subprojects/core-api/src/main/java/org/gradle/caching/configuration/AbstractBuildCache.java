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

package org.gradle.caching.configuration;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.inject.Inject;

/**
 * Base implementation for build cache service configuration.
 *
 * @since 3.5
 */
public abstract class AbstractBuildCache implements BuildCache, BuildCachePushProperty {

    @Inject
    protected abstract ObjectFactory getObjects();

    private boolean enabled = true;
    private final Property<Boolean> push = getObjects().property(Boolean.class).convention(false);

    /**
     * {@inheritDoc}
     */
    @Override
    public Property<Boolean> getPush() {
        return push;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
    public boolean isPush() {
        return push.getOrElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPush(boolean push) {
        this.push.set(push);
    }
}
