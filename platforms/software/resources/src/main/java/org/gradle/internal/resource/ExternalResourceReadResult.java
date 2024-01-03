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

package org.gradle.internal.resource;

import javax.annotation.Nullable;

/**
 * @since 4.0
 */
public class ExternalResourceReadResult<T> {

    private final long bytesRead;
    private final T result;

    private ExternalResourceReadResult(long bytesRead, T result) {
        this.bytesRead = bytesRead;
        this.result = result;
    }

    public static ExternalResourceReadResult<Void> of(long bytesRead) {
        return new ExternalResourceReadResult<Void>(bytesRead, null);
    }

    public static <T> ExternalResourceReadResult<T> of(long bytesRead, T t) {
        return new ExternalResourceReadResult<T>(bytesRead, t);
    }

    /**
     * The number of <b>content</b> bytes read.
     * <p>
     * This is not guaranteed to be the number of bytes <b>transferred</b>.
     * For example, this resource may be content encoded (e.g. compression, fewer bytes transferred).
     * Or, it might be transfer encoded (e.g. HTTP chunked transfer, more bytes transferred).
     * Or, both.
     * Therefore, it is not necessarily an accurate input into transfer rate (a.k.a. throughput) calculations.
     * <p>
     * Moreover, it represents the content bytes <b>read</b>, not transferred.
     * If the read operation only reads a subset of what was transmitted, this number will be the read byte count.
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Any final result of the read operation.
     */
    @Nullable
    public T getResult() {
        return result;
    }
}
