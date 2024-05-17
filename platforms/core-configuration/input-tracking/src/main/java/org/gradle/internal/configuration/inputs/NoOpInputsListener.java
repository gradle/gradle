/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.configuration.inputs;

import javax.annotation.Nullable;
import java.io.File;

class NoOpInputsListener implements InstrumentedInputsListener {
    static final InstrumentedInputsListener INSTANCE = new NoOpInputsListener();

    private NoOpInputsListener() {}

    @Override
    public void systemPropertyQueried(String key, @Nullable Object value, String consumer) {}

    @Override
    public void systemPropertyChanged(Object key, @Nullable Object value, String consumer) {}

    @Override
    public void systemPropertyRemoved(Object key, String consumer) {}

    @Override
    public void systemPropertiesCleared(String consumer) {}

    @Override
    public void envVariableQueried(String key, @Nullable String value, String consumer) {}

    @Override
    public void externalProcessStarted(String command, String consumer) {}

    @Override
    public void fileOpened(File file, String consumer) {}

    @Override
    public void fileObserved(File file, String consumer) {}

    @Override
    public void fileSystemEntryObserved(File file, String consumer) {}

    @Override
    public void directoryContentObserved(File file, String consumer) {}
}
