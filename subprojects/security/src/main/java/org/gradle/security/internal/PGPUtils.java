/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.security.internal;

import org.bouncycastle.openpgp.PGPPublicKey;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PGPUtils {

    private PGPUtils() {
    }

    /**
     * A custom method to get user ids since original method `PGPPublicKey.getUserIDs()` can fail fast in case user id is not correctly encoded in UTF-8.
     * The original method fails because it is very strict at conversion from bytes to UTF-8 string.
     * <p>
     * Example of dependencies with "broken" public key is `com.google.auto.value:auto-value-annotations`.
     */
    public static List<String> getUserIDs(PGPPublicKey pk) {
        List<String> userIds = new ArrayList<>();
        pk.getRawUserIDs().forEachRemaining(id -> {
            userIds.add(new String(id, StandardCharsets.UTF_8));
        });
        return userIds;
    }

}
