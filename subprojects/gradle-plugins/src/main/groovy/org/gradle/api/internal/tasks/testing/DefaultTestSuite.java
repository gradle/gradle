/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import java.io.Serializable;

public class DefaultTestSuite implements TestInternal, Serializable {
    private final Object id;
    private final String name;

    public DefaultTestSuite(Object id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("test '%s'", name);
    }

    public Object getId() {
        return id;
    }

    public boolean isComposite() {
        return true;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return null;
    }
}
