/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.file.FileCollection;

import javax.annotation.Nullable;
import java.io.File;

/**
 * This class maintains the global and per-thread state of the instrumentation listener.
 */
class InstrumentedListenerHolder {
    private static final Instrumented.Listener NO_OP = createNoOp();

    private volatile Instrumented.Listener global = NO_OP;
    private final ThreadLocal<Boolean> enabledForCurrentThread = ThreadLocal.withInitial(() -> true);

    public void setGlobalListener(Instrumented.Listener listener) {
        global = listener;
    }

    public void discardGlobalListener() {
        global = NO_OP;
    }

    public Instrumented.Listener listenerForCurrentThread() {
        return enabledForCurrentThread.get() ? global : NO_OP;
    }

    public void disableListenerForCurrentThread() {
        enabledForCurrentThread.set(false);
    }

    public void restoreListenerForCurrentThread() {
        enabledForCurrentThread.set(true);
    }

    private static Instrumented.Listener createNoOp() {
        return new Instrumented.Listener() {
            @Override
            public void systemPropertyQueried(String key, @Nullable Object value, String consumer) {
            }

            @Override
            public void systemPropertyChanged(Object key, @Nullable Object value, String consumer) {
            }

            @Override
            public void systemPropertyRemoved(Object key, String consumer) {
            }

            @Override
            public void systemPropertiesCleared(String consumer) {
            }

            @Override
            public void envVariableQueried(String key, @Nullable String value, String consumer) {
            }

            @Override
            public void externalProcessStarted(String command, String consumer) {
            }

            @Override
            public void fileOpened(File file, String consumer) {
            }

            @Override
            public void fileObserved(File file, String consumer) {
            }

            @Override
            public void fileCollectionObserved(FileCollection fileCollection, String consumer) {
            }
        };
    }
}
