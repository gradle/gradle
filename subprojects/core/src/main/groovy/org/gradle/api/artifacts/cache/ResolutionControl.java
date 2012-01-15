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
package org.gradle.api.artifacts.cache;

import java.util.concurrent.TimeUnit;

public interface ResolutionControl {
    // command methods
    void cacheFor(int value, TimeUnit units); // use cached value if no older than specified duration, resolve once per build if not

    void useCachedResult(); // use cached value only, fail if no cached result, ie offline mode

    void invalidate(); // discard cached value, forces a resolve regardless of cached value or whether already resolved in this build
}
