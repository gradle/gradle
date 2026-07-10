/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.verifier;

import java.net.URI;
import java.util.Collection;

/**
 * Use {@link HttpRedirectVerifierFactory#create} to instantiate an instance of this.
 */
@FunctionalInterface
public interface HttpRedirectVerifier {
    /**
     * Perform verification on the URI's in an HTTP request's redirect chain.
     */
    void validateRedirects(Collection<URI> redirectLocations);
}
