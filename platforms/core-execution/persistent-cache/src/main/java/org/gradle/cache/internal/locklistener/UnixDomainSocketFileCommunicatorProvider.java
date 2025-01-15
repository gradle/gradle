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

package org.gradle.cache.internal.locklistener;

import com.google.common.base.Suppliers;
import org.gradle.api.NonNullApi;

import java.util.function.Supplier;

/**
 * Provides a {@link UnixDomainSocketFileLockCommunicator} that communicates with a Unix domain socket file.
 */
@NonNullApi
public class UnixDomainSocketFileCommunicatorProvider {

    private final Supplier<UnixDomainSocketFileLockCommunicator> communicator;

    public UnixDomainSocketFileCommunicatorProvider() {
        communicator = Suppliers.memoize(UnixDomainSocketFileLockCommunicator::new);
    }

    public UnixDomainSocketFileLockCommunicator getCommunicator() {
        return communicator.get();
    }
}
