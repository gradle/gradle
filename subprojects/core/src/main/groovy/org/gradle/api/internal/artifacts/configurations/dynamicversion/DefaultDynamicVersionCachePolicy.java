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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultDynamicVersionCachePolicy implements DynamicVersionCachePolicy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;

    private final List<Action<CachedDynamicVersion>> userActionList = new ArrayList<Action<CachedDynamicVersion>>();
    private final Action<CachedDynamicVersion> defaultAction = new FixedAgeDynamicVersionCheck(SECONDS_IN_DAY, TimeUnit.SECONDS);

    public void expireDynamicVersionsAfter(int value, TimeUnit unit) {
        this.userActionList.clear();
        this.userActionList.add(new FixedAgeDynamicVersionCheck(value, unit));
    }

    public boolean mustCheckForUpdates(final ResolvedModule module, final long ageMillis) {
        DefaultCachedDynamicVersion details = new DefaultCachedDynamicVersion(module, ageMillis);

        if (userActionList.isEmpty()) {
            // Use the default only if 
            defaultAction.execute(details);
        } else {
            for (Action<CachedDynamicVersion> userAction : userActionList) {
                userAction.execute(details);
            }
        }

        return details.mustCheck;
    }
    

    private class DefaultCachedDynamicVersion implements CachedDynamicVersion {
        public boolean mustCheck;
        private final ResolvedModule module;
        private final long ageMillis;

        private DefaultCachedDynamicVersion(ResolvedModule module, long ageMillis) {
            this.ageMillis = ageMillis;
            this.module = module;
        }

        public void mustCheckForUpdates() {
            mustCheck = true;
        }

        public ResolvedModule getModule() {
            return module;
        }

        public long getAgeMillis() {
            return ageMillis;
        }
    }

    private class FixedAgeDynamicVersionCheck implements Action<CachedDynamicVersion> {
        private long expiryMillis;

        private FixedAgeDynamicVersionCheck(int value, TimeUnit units) {
            expiryMillis = TimeUnit.MILLISECONDS.convert(value, units);
        }

        public void execute(CachedDynamicVersion cachedDynamicVersion) {
            if (cachedDynamicVersion.getAgeMillis() >= expiryMillis) {
                cachedDynamicVersion.mustCheckForUpdates();
            }
        }
    }

}
