/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when the build init plugin fails to initialize a new project.
 */
public class BuildInitException extends GradleException implements ResolutionProvider {
    private final List<String> resolutions;

    public BuildInitException(String message) {
        this(message, Collections.emptyList());
    }

    public BuildInitException(String message, Iterable<String> resolutions) {
        super(message);
        this.resolutions = ImmutableList.copyOf(resolutions);
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
