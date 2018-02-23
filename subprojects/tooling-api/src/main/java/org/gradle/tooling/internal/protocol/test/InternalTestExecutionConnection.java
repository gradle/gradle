/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.protocol.test;

import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.InternalProtocolInterface;

/**
 * Mixed into a provider connection to allow tests to be executed.
 *
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 2.6-rc-1.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 2.6-rc-1.</p>
 *
 * @since 2.6-rc-1
 */
public interface InternalTestExecutionConnection extends InternalProtocolInterface {
    BuildResult<?> runTests(InternalTestExecutionRequest testExecutionRequest, InternalCancellationToken cancellationToken, BuildParameters operationParameters);
}
