/*
 * Copyright 2018 the original author or authors.
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

/**
 * This package contains the base implementations for the {@link org.gradle.api.provider.Provider provider} and {@link org.gradle.api.provider.Property lazy property} abstractions.
 *
 * <p>Providers are container objects that provide a value of a specific type.</p>
 *
 * <p>Properties are providers with configurable values.</p>
 *
 * <h2>Provider</h2>
 *
 * <p>For the public API, see {@link org.gradle.api.provider.Provider Provider}.</p>
 *
 * <p>For more information on providers, beyond what the user-facing documentation and API offer,
 * see the {@link org.gradle.api.internal.provider.ProviderInternal} interface.</p>
 *
 * <p>Also, make sure to become familiar with {@link org.gradle.api.internal.provider.AbstractMinimalProvider},
 * the base class for all provider (and property) implementations.</p>
 *
 * <h3>Lazy properties</h3>
 *
 * <p>For the public API, see {@link org.gradle.api.provider.Property Property}.</p>
 *
 * <p>For the base internal implementation, see {@link org.gradle.api.internal.provider.AbstractProperty}.</p>
 *
 * <p>Other classes implementing <code>Property</code> in this module:</p>
 * <ul>
 *     <li>{@link org.gradle.api.internal.provider.DefaultProperty} - a concrete implementation for general-purpose properties.</li>
 *     <li>{@link org.gradle.api.internal.provider.AbstractCollectionProperty} - implements the API for
 *     collection properties (specified via {@link org.gradle.api.provider.HasMultipleValues HasMultipleValues}) namely {@link org.gradle.api.provider.ListProperty list} and {@link org.gradle.api.provider.SetProperty set} properties).</li>
 *     <li>{@link org.gradle.api.internal.provider.DefaultMapProperty} - implements the {@link org.gradle.api.provider.MapProperty map} property API.
 *     Note that this class and <code>AbstractCollectionProperty</code> have a lot of duplication, as they implement very similar contracts.
 * </ul>
 */
@NonNullApi
package org.gradle.api.internal.provider;

import org.gradle.api.NonNullApi;
