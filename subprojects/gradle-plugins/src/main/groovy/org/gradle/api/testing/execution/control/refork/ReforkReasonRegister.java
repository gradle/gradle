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
package org.gradle.api.testing.execution.control.refork;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class ReforkReasonRegister {
    private static final ReforkReasonRegister INSTANCE = new ReforkReasonRegister();

    static {
        addReforkReason(new AmountOfTestCasesReforkReason());
        addReforkReason(new ForkMemoryLowReforkReason());
    }

    private final Map<ReforkReasonKey, ReforkReason> reforkReasons;

    private ReforkReasonRegister() {
        reforkReasons = new ConcurrentHashMap<ReforkReasonKey, ReforkReason>();
    }

    public static void addReforkReason(ReforkReason reforkReason) {
        if (reforkReason == null) {
            throw new IllegalArgumentException("reforkReason == null!");
        }

        INSTANCE.reforkReasons.put(reforkReason.getKey(), reforkReason);
    }

    public static ReforkReason getReforkReason(ReforkReasonKey reforkReasonKey) {
        if (reforkReasonKey == null) {
            throw new IllegalArgumentException("reforkReasonKey == null!");
        }

        return INSTANCE.reforkReasons.get(reforkReasonKey);
    }
}
