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

/**
 * The base of all kinds of failure results.
 * <p>
 * The “value” of this result will be an exception that represents the failure. It may not be {@code null}.
 */
public class Failure extends Result<Throwable> {
    
    public Failure(Throwable value) {
        super(assertNotNull(value));
    }
    
    private static Throwable assertNotNull(Throwable value) {
        if (value == null) {
            throw new IllegalArgumentException("The value parameter of a failure cannot be null");
        }
        
        return value;
    }
}