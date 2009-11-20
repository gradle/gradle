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

package org.gradle.api.changedetection.digest;

import org.apache.commons.lang.StringUtils;

import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
class DefaultDigesterCache implements DigesterCache {

    private final Map<String, MessageDigest> cache;
    private final DigesterFactory digesterFactory;

    DefaultDigesterCache(DigesterFactory digesterFactory) {
        if (digesterFactory == null) {
            throw new IllegalArgumentException("digesterFactory is null!");
        }

        this.cache = new ConcurrentHashMap<String, MessageDigest>();
        this.digesterFactory = digesterFactory;
    }

    public MessageDigest getDigester(String cacheId) {
        if (StringUtils.isEmpty(cacheId)) {
            throw new IllegalArgumentException("cacheId is empty!");
        }

        MessageDigest digester = cache.get(cacheId);
        if (digester == null) {
            digester = digesterFactory.createDigester();
            cache.put(cacheId, digester);
        } else {
            digester.reset();
        }

        return digester;
    }

    public DigesterFactory getDigesterFactory() {
        return digesterFactory;
    }
}
