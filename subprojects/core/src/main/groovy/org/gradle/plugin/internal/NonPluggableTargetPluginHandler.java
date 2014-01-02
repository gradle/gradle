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

package org.gradle.plugin.internal;

import org.gradle.plugin.PluginHandler;

import java.util.Map;

public class NonPluggableTargetPluginHandler implements PluginHandler {

    private final Object target;

    public NonPluggableTargetPluginHandler(Object target) {
        this.target = target;
    }

    public void apply(Map<String, ?> attributes) {
        throw fail();
    }

    private RuntimeException fail() {
        return new UnsupportedOperationException("Script target " + target + " cannot have plugins applied to it");
    }
}
