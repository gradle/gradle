/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import com.google.common.collect.Streams;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

@NullMarked
public class DefaultResilientGradleBuild extends DefaultGradleBuild {
    private boolean failed = false;
    @Nullable
    private String failure = null;

    public DefaultGradleBuild setFailure(@Nullable String failure) {
        this.failed = failure != null;
        this.failure = failure;

        return this;
    }

    public boolean didItFail() {
        return failed ||
            getBuildsStream().anyMatch(DefaultResilientGradleBuild::didItFail);
    }

    @Nullable
    public String getFailure() {
        if (failure == null) {
            return getBuildsStream()
                .map(DefaultResilientGradleBuild::getFailure)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
        return failure;
    }

    private Stream<DefaultResilientGradleBuild> getBuildsStream() {
        return Streams.concat(allBuilds.stream(), includedBuilds.stream()).map(DefaultResilientGradleBuild.class::cast);
    }
}
