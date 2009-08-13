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

package org.gradle.api.changedetection.state;

import org.apache.commons.lang.StringUtils;

/**
 * @author Tom Eyckmans
 */
class StateFileItem {
    private final String key;
    private final String digest;

    StateFileItem(String key, String digest) {
        if ( key == null ) throw new IllegalArgumentException("key is null!");
        if ( StringUtils.isEmpty(digest) ) throw new IllegalArgumentException("digest is empty!");
        this.key = key;
        this.digest = digest;
    }

    public String getKey() {
        return key;
    }

    public String getDigest() {
        return digest;
    }
}
