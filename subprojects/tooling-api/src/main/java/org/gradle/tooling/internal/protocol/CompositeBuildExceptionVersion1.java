/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.tooling.composite.BuildIdentity;

/**
 * A wrapper around a composite build failure, to distinguish it from an infrastructure failure. The details of the failure are made available in the cause of this exception.
 *
 * DO NOT CHANGE THIS CLASS. It is part of the cross-version protocol.
 *
 * @since 2.13
 */
public class CompositeBuildExceptionVersion1 extends RuntimeException {
    private final BuildIdentity buildIdentity;

    public CompositeBuildExceptionVersion1(Throwable throwable, DefaultBuildIdentity buildIdentity) {
        super(throwable);
        this.buildIdentity = buildIdentity;
    }

    public BuildIdentity getBuildIdentity() {
        return buildIdentity;
    }
}
