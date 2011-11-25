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
package org.gradle.api.internal.artifacts.configurations.dynamicversion;

import org.gradle.api.artifacts.ResolvedModuleVersion;

import java.util.concurrent.TimeUnit;

public class DefaultCachePolicy implements CachePolicy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;
    private Duration dynamicVersionExpiry = new Duration(SECONDS_IN_DAY, TimeUnit.SECONDS);
    private Duration changingModuleExpiry = new Duration(SECONDS_IN_DAY, TimeUnit.SECONDS);


    public void cacheDynamicVersionsFor(int value, TimeUnit unit) {
        dynamicVersionExpiry = new Duration(value, unit);
    }

    public void cacheChangingModulesFor(int value, TimeUnit units) {
        changingModuleExpiry = new Duration(value, units);
    }

    public boolean mustRefreshDynamicVersion(final ResolvedModuleVersion version, final long ageMillis) {
        return ageMillis >= dynamicVersionExpiry.getMillis();
    }

    public boolean mustRefreshChangingModule(final ResolvedModuleVersion version, final long ageMillis) {
        return ageMillis >= changingModuleExpiry.getMillis();
    }

    public boolean mustRefreshMissingArtifact(long ageMillis) {
        return ageMillis >= changingModuleExpiry.getMillis();
    }

    private static class Duration {
        private final int value;
        private final TimeUnit units;

        private Duration(int value, TimeUnit units) {
            this.value = value;
            this.units = units;
        }
        
        public long getMillis() {
            return TimeUnit.MILLISECONDS.convert(value, units);
        }
    }

}
