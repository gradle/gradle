/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.execution.SharedResource;

public class DefaultSharedResource implements SharedResource {
    private final String name;
    private int leases = 1;

    public DefaultSharedResource(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getLeases() {
        return leases;
    }

    @Override
    public void setLeases(int leases) {
        this.leases = leases;
    }

    @Override
    public String toString() {
        return "DefaultSharedResource{" +
            "name='" + name + '\'' +
            ", leases=" + leases +
            '}';
    }
}
