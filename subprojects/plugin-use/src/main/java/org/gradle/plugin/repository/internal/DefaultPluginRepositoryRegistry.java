/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository.internal;

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.ThreadSafe;
import org.gradle.plugin.repository.GradlePluginPortal;
import org.gradle.plugin.repository.PluginRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ThreadSafe
public class DefaultPluginRepositoryRegistry implements PluginRepositoryRegistry {
    private final List<PluginRepository> repositories;
    private final AtomicBoolean locked;
    private final AtomicBoolean portalAdded;

    public DefaultPluginRepositoryRegistry() {
        this.repositories = new ArrayList<PluginRepository>();
        locked = new AtomicBoolean(false);
        portalAdded = new AtomicBoolean(false);
    }

    @Override
    public void add(PluginRepository pluginRepository) {
        if (pluginRepository instanceof GradlePluginPortal) {
            addPortal(pluginRepository);
        } else {
            addRepository(pluginRepository);
        }
    }

    @Override
    public void lock() {
        locked.set(true);
    }

    @Override
    public ImmutableList<PluginRepository> getPluginRepositories() {
        if (locked.get()) {
            return ImmutableList.copyOf(repositories);
        } else {
            throw new IllegalStateException("Cannot read the PluginRepository list when the Registry is unlocked.");
        }
    }

    private void addPortal(PluginRepository pluginPortal) {
        if (portalAdded.compareAndSet(false, true)) {
            addRepository(pluginPortal);
        } else {
            throw new IllegalStateException("Cannot add Gradle Plugin Portal more than once.");
        }
    }

    private void addRepository(PluginRepository pluginRepository) {
        if (!locked.get()) {
            repositories.add(pluginRepository);
        } else {
            throw new IllegalStateException("Cannot add a PluginRepository when the Registry is locked.");
        }
    }
}
