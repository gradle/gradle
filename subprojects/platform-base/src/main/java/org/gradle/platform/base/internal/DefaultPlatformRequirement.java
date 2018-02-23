/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

public class DefaultPlatformRequirement implements PlatformRequirement {
    private final String platformName;

    public static PlatformRequirement create(String name) {
        return new DefaultPlatformRequirement(name);
    }

    public DefaultPlatformRequirement(String platformName) {
        this.platformName = platformName;
    }

    @Override
    public String getPlatformName() {
        return platformName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultPlatformRequirement that = (DefaultPlatformRequirement) o;
        return platformName.equals(that.platformName);

    }

    @Override
    public int hashCode() {
        return platformName.hashCode();
    }

    @Override
    public String toString() {
        return getPlatformName();
    }
}
