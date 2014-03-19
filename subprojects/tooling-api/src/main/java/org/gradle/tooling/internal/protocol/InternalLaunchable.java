/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

/**
 * A marker interface for launchables.
 *
 * The real implementation exists elsewhere and is only interesting for provider.
 * Consumer will see public part of the contract from *.model.* packages and will send it back
 * to provider as it is when a build is launched.
 *
 * @since 1.12
 */
public interface InternalLaunchable extends InternalProtocolInterface {
}
