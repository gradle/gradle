/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote.internal;

/**
 * A {@code PlaceholderException} is used when an exception cannot be serialized or deserialized.
 */
public class PlaceholderException extends RuntimeException {
    private String exceptionClassName;
    private final String toString;
    private final RuntimeException toStringRuntimeEx;

    public PlaceholderException(String exceptionClassName, String message, String toString, RuntimeException toStringException, Throwable cause) {
        super(message, cause);
        this.exceptionClassName = exceptionClassName;
        this.toString = toString;
        this.toStringRuntimeEx = toStringException;
    }

    public String getExceptionClassName() {
        return exceptionClassName;
    }

    public String toString() {
        if(toStringRuntimeEx !=null){
            throw toStringRuntimeEx;
        }
        return toString;
    }
}
