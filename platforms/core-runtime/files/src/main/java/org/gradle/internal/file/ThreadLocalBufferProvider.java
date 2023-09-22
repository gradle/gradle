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

package org.gradle.internal.file;

/**
 * Provides a buffer that is bound to the current thread.
 */
public class ThreadLocalBufferProvider implements BufferProvider {
    private final ThreadLocal<byte[]> copyBuffers;

    public ThreadLocalBufferProvider(final int bufferSize) {
        this.copyBuffers = new ThreadLocal<byte[]>() {
            @Override
            protected byte[] initialValue() {
                return new byte[bufferSize];
            }
        };
    }

    @Override
    public byte[] getBuffer() {
        return copyBuffers.get();
    }
}
