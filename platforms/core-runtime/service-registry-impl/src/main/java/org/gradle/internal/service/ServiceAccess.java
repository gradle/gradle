/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.service;


import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for {@link ServiceAccessToken} and {@link ServiceAccessScope}.
 */
class ServiceAccess {

    private ServiceAccess() {}

    private static final ServiceAccessScope PUBLIC = new ServiceAccessScope() {
        @Override
        public boolean contains(@Nullable ServiceAccessToken token) {
            return true;
        }

        @Override
        public String toString() {
            return "Public";
        }
    };

    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    /**
     * Creates a new unique token.
     */
    public static ServiceAccessToken createToken(String ownerDisplayName) {
        return new DefaultServiceAccessToken(NEXT_ID.incrementAndGet(), ownerDisplayName);
    }

    /**
     * Returns a scope that assumes to contain every possible token.
     */
    public static ServiceAccessScope getPublicScope() {
        return PUBLIC;
    }

    /**
     * Returns a scope that contains only the provided token.
     */
    public static ServiceAccessScope getPrivateScope(ServiceAccessToken token) {
        return new PrivateAccessScope(token);
    }

    private static class PrivateAccessScope implements ServiceAccessScope {
        private final ServiceAccessToken ownerToken;

        public PrivateAccessScope(ServiceAccessToken ownerToken) {
            this.ownerToken = ownerToken;
        }

        @Override
        public boolean contains(@Nullable ServiceAccessToken token) {
            return ownerToken.equals(token);
        }

        @Override
        public String toString() {
            return "Private(" + ownerToken + ")";
        }
    }
}
