/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.internal;

import org.gradle.swiftpm.Product;

import java.io.Serializable;

public abstract class AbstractProduct implements Product, Serializable {
    private final String name;
    private final DefaultTarget target;

    AbstractProduct(String name, DefaultTarget target) {
        this.name = name;
        this.target = target;
    }

    @Override
    public String getName() {
        return name;
    }

    public DefaultTarget getTarget() {
        return target;
    }

    public abstract boolean isExecutable();
}
