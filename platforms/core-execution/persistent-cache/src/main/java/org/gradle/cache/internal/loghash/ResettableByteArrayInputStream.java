/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.cache.internal.loghash;

import java.io.ByteArrayInputStream;

/**
 * A ByteArrayInputStream that allows resetting the underlying data without creating a new instance.
 * Used by the value decoder to reuse the stream across reads.
 */
class ResettableByteArrayInputStream extends ByteArrayInputStream {

    ResettableByteArrayInputStream() {
        super(new byte[0]);
    }

    void setData(byte[] data, int length) {
        this.buf = data;
        this.pos = 0;
        this.count = length;
        this.mark = 0;
    }
}
