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
import com.google.common.collect.Lists;
import org.gradle.BuildAdapter;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.event.ListenerManager;
import org.gradle.plugin.repository.PluginRepository;

import java.util.List;

public class DefaultPluginRepositoryRegistry implements PluginRepositoryRegistry {
    private final List<PluginRepositoryInternal> repositories = Lists.newArrayList();;
    private boolean locked;

    public DefaultPluginRepositoryRegistry(ListenerManager listenerManager) {
        listenerManager.addListener(new BuildAdapter(){
            @Override
            public void projectsLoaded(Gradle gradle) {
                lock();
            }
        });
    }

    @Override
    public void add(PluginRepository pluginRepository) {
        if (locked) {
            throw new IllegalStateException("Cannot add a PluginRepository after projects have been loaded.");
        } else {
            repositories.add((PluginRepositoryInternal) pluginRepository);
        }
    }

    private void lock() {
        locked = true;
        for (PluginRepositoryInternal repository : repositories) {
            repository.lock("Projects have already been loaded.");
        }
    }

    @Override
    public ImmutableList<PluginRepositoryInternal> getPluginRepositories() {
        return ImmutableList.copyOf(repositories);
    }

}
