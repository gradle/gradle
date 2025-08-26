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

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultResilientGradleBuild extends DefaultGradleBuild {
    private boolean failed = false;
    private String failure;

    public DefaultGradleBuild setFailure(String failure) {
        this.failed = failure != null;
        this.failure = failure;

        return this;
    }

    public boolean didItFail(){
        return failed ||
            allBuilds.stream().map(DefaultResilientGradleBuild.class::cast).anyMatch(DefaultResilientGradleBuild::didItFail) ||
            includedBuilds.stream().map(DefaultResilientGradleBuild.class::cast).anyMatch(DefaultResilientGradleBuild::didItFail);
    }

    public String getFailure() {
        return failure;
    }
}
