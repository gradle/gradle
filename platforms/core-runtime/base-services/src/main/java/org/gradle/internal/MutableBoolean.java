/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serializable;

/**
 * A non-thread-safe type to hold a mutable boolean value.
 */
@NotThreadSafe
public final class MutableBoolean implements Serializable {
    private boolean value;

    public MutableBoolean() {
        this(false);
    }

    public MutableBoolean(boolean initialValue) {
        this.value = initialValue;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public boolean get() {
        return value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
