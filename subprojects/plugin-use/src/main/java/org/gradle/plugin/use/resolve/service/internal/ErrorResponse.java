/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

/**
 * Defines the JSON protocol for plugin resolution service error responses.
 */
public class ErrorResponse {
    public final String errorCode;
    public final String message;

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public boolean is(Code code) {
        return code.name().equals(errorCode);
    }

    public enum Code {
        UNKNOWN_PLUGIN,
        UNKNOWN_PLUGIN_VERSION
    }

    @Override
    public String toString() {
        return "{errorCode='" + errorCode + '\'' + ", message='" + message + '\'' + '}';
    }
}
