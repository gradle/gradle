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
package org.gradle.testing.junit5.internal;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.junit.platform.launcher.TestIdentifier;

import java.io.Serializable;

public class JUnitPlatformEvent implements Serializable {
    private final TestDescriptorInternal test;
    private final Type type;
    private final long time;
    private final String message;
    private final Throwable error;

    public JUnitPlatformEvent(TestDescriptorInternal test, Type type, long time, String message, Throwable error) {
        this.test = test;
        this.type = type;
        this.time = time;
        this.message = message;
        this.error = error;
    }

    public TestDescriptorInternal getTest() {
        return test;
    }

    public Type getType() {
        return type;
    }

    public long getTime() {
        return time;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getError() {
        return error;
    }

    public enum Type {
        START,
        OUTPUT,
        SKIPPED,
        FAILED,
        SUCCEEDED;
    }
}
