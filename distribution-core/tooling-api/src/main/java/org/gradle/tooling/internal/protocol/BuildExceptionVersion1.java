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
package org.gradle.tooling.internal.protocol;

/**
 * A wrapper around a build failure, to distinguish it from an infrastructure failure. The details of the
 * failure are made available in the cause of this exception.
 *
 * DO NOT CHANGE THIS CLASS. It is part of the cross-version protocol.
 *
 * @since 1.0-milestone-3
 */
public class BuildExceptionVersion1 extends RuntimeException {
    public BuildExceptionVersion1(Throwable throwable) {
        super(throwable);
    }
}
