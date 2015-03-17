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

package org.gradle.launcher.daemon.server.health;

import org.gradle.api.GradleException;

import static java.lang.String.format;

class DaemonStatus {

    public static final String EXPIRE_AT_PROPERTY = "org.gradle.daemon.performance.expire-at";
    static final int DEFAULT_EXPIRE_AT = 0;

    boolean isDaemonTired(DaemonStats stats) {
        String expireAt = System.getProperty(EXPIRE_AT_PROPERTY);
        int threshold = parseValue(expireAt, DEFAULT_EXPIRE_AT);
        return threshold != 0 //zero means the feature is off
                && stats.getMemoryUsed() > 85 //the daemon is not tired if the memory is not sufficiently exhausted
                && stats.getCurrentPerformance() <= threshold; //performance below threshold
    }

    private static int parseValue(String expireAt, int defaultValue) {
        if (expireAt == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(expireAt);
        } catch (Exception e) {
            throw new GradleException(format(
                    "System property '%s' has incorrect value: '%s'. The value needs to be integer.",
                    EXPIRE_AT_PROPERTY, expireAt));
        }
    }
}
