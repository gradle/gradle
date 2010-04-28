/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.testing.fabric.TestFramework;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFramework implements TestFramework {
    protected final String id;
    protected final String name;

    protected AbstractTestFramework(final String id, final String name) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("id == empty!");
        }
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("name == empty!");
        }
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
