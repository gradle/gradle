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

package org.gradle.api.internal.attributes;

import org.gradle.api.Named;

import java.io.Serializable;

/**
 * This class lives in the Java source set to avoid Groovy adding a bunch of extra stuff to it.
 * This is used in {@link BaseAttributeContainerTest}, and must not contain extra references to
 * classes, as it is loaded in an isolated classloader.
 */
public final class MyNamed implements Named, Serializable {

    private final String name;

    public MyNamed(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

}
