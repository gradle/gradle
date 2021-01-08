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
 * Initial configuration for a provider connection.
 *
 * <p>This is a mostly-empty interface. Instances are queried dynamically to see which properties they support. See {@code ProviderConnectionParameters} to see the configuration expected by the provider,
 * and {@link org.gradle.tooling.internal.consumer.ConnectionParameters} to see the configuration provided by the consumer.
 *
 * @since 1.2-rc-1
 */
public interface ConnectionParameters extends InternalProtocolInterface {
    String getConsumerVersion();
}
