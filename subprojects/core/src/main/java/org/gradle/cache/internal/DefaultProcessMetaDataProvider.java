/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.internal.nativeintegration.ProcessEnvironment;

public class DefaultProcessMetaDataProvider implements ProcessMetaDataProvider {
    private final ProcessEnvironment environment;

    public DefaultProcessMetaDataProvider(ProcessEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public String getProcessIdentifier() {
        Long pid = environment.maybeGetPid();
        return pid == null ? "gradle" : String.valueOf(pid);
    }

    @Override
    public String getProcessDisplayName() {
        return "gradle";
    }
}
