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

package org.gradle.initialization;

import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.concurrent.ParallelismConfigurationListener;
import org.gradle.internal.event.ListenerManager;

public class DefaultParallelismConfigurationManager implements ParallelismConfigurationManager {
    private final ListenerManager listenerManager;
    private final ParallelismConfigurationListener broadcaster;
    private ParallelismConfiguration parallelismConfiguration = DefaultParallelismConfiguration.DEFAULT;

    public DefaultParallelismConfigurationManager(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        this.broadcaster = listenerManager.getBroadcaster(ParallelismConfigurationListener.class);
    }

    @Override
    public ParallelismConfiguration getParallelismConfiguration() {
        return parallelismConfiguration;
    }

    @Override
    public void setParallelismConfiguration(ParallelismConfiguration parallelismConfiguration) {
        this.parallelismConfiguration = parallelismConfiguration;
        broadcaster.onParallelismConfigurationChange(parallelismConfiguration);
    }

    @Override
    public void addListener(ParallelismConfigurationListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removeListener(ParallelismConfigurationListener listener) {
        listenerManager.removeListener(listener);
    }
}
