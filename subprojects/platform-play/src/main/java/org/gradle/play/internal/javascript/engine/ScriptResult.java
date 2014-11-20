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

package org.gradle.play.internal.javascript.engine;

/**
 *
 */
public class ScriptResult {
    private final int status;
    private final Throwable exception;
    public static final int SUCCESS = 0;

    public ScriptResult(int status) {
        this.status = status;
        this.exception = null;
    }

    public ScriptResult(Throwable exception) {
        status = -1;
        this.exception = exception;
    }

    public int getStatus() {
        return status;
    }

    public Throwable getException() {
        return exception;
    }
}
