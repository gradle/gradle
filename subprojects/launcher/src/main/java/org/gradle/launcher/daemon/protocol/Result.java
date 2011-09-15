/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.protocol;

import java.io.Serializable;

/**
 * The supertype of all objects sent from the daemon server back to the client.
 * <p>
 * Specific subclass types carry extra context, e.g. whether it was a failure or successful result.
 * <p>
 * The meaning of the value parameter is specific to each concrete subclass. The validity of {@code null}
 * is also to be defined by each subclass. This implementation does allow null values.
 */
abstract public class Result<T> implements Serializable {

    private final T value;

    public Result(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return String.format("DaemonCommandResult[type=%s, value=%s]", getClass().getSimpleName(), getValue());
    }
}
