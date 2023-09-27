/*
 * Copyright 2012 the original author or authors.
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
 * The result of running a build.
 *
 * <p>This is a mostly-empty interface. Instances are queried dynamically to see which properties they support.
 * See {@code ProviderBuildResult} for details on properties supported by the provider.
 *
 * @since 1.2-rc-1
 */
public interface BuildResult<T> extends InternalProtocolInterface {
    T getModel();
}
