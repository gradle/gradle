/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.base.Optional;

public class ConfiguredOptions {

    private Optional<Object> target = Optional.absent();

    private Optional<String> version = Optional.absent();

    public boolean isTargetSet() {
        return target.isPresent();
    }

    public Object getTarget() {
        return target.orNull();
    }

    public void setTarget(Object target) {
        this.target = Optional.fromNullable(target);
    }

    public boolean isVersionSet() {
        return version.isPresent();
    }

    public String getVersion() {
        return version.orNull();
    }

    public void setVersion(String version) {
        this.version = Optional.fromNullable(version);
    }
}
