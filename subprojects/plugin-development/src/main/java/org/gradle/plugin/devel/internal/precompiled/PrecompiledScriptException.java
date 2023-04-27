/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

public class PrecompiledScriptException extends GradleException implements ResolutionProvider {
    private final List<String> resolutions;

    public PrecompiledScriptException(String message, String resolution) {
        super(message);
        this.resolutions = Collections.singletonList(resolution);
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
