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

/**
 * @author Tom Eyckmans
 */
public class DigestObjectFactory {
    public static DigesterCache createDigesterCache(final DigesterFactory digesterFactory) {
        return new DefaultDigesterCache(digesterFactory);
    }

    public static DigesterCache createShaDigesterCache() {
        return createDigesterCache(createShaDigesterFactory());
    }

    public static DigesterUtil createDigesterUtil(final DigesterUtilStrategy digesterUtilStrategy) {
        return new DefaultDigesterUtil(digesterUtilStrategy);
    }

    public static DigesterUtil createMetaDigesterUtil() {
        return createDigesterUtil(new MetaDigesterUtilStrategy());
    }

    public static DigesterUtil createMetaContentDigesterUtil() {
        return createDigesterUtil(new MetaContentDigesterUtilStrategy());
    }

    public static DigesterFactory createShaDigesterFactory() {
        return new ShaDigesterFactory();
    }
}
