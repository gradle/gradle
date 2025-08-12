/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures.compatibility;

public enum CoverageContext {
    DEFAULT("default"), LATEST("latest"), PARTIAL("partial"), FULL("all"), UNKNOWN(null);

    public final String selector;

    CoverageContext(String selector) {
        this.selector = selector;
    }

    static CoverageContext from(String requested) {
        for (CoverageContext context : values()) {
            if (context != UNKNOWN && context.selector.equals(requested)) {
                return context;
            }
        }
        return UNKNOWN;
    }
}
