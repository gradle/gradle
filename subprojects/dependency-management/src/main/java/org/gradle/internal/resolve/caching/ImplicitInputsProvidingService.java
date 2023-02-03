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
package org.gradle.internal.resolve.caching;

import javax.annotation.Nullable;

/**
 * Interface for services which support recording the "implicit inputs" they generate.
 * Whenever a rule calls a service, it may have side effects that needs to be taken
 * into account when checking if the rule is up-to-date. For example, if a rule performs
 * an HTTP query, we know that there is an implicit input to the rule which is the result
 * of the HTTP query (the external resource).
 *
 * Whenever we need to check if the rule is up-to-date, we must be able to ask the service
 * if the resource is still up-to-date. For this, we need to record calls to the service
 * and their result.
 *
 * In the external resource example, a record may consist of the external resource URI and
 * the external resource text. Then when we need to check if the resource is up-to-date,
 * we can ask the service by calling {@code isUpToDate(IN, OUT)} with
 * the URI as an input.
 *
 * It's up to the service implementation to determine:
 *
 * - the type of the input which allows requesting its up-to-date ness ({@link IN}
 * - the type of the output which allows checking if the result of calling the service is the same ({@link OUT}
 *
 * Both have to be serializable, and it's encouraged to use the minimal footprint which allows
 * determining up-to-date status. For example, a SHA1 of a resource might be enough, rather than storing
 * the whole resource itself.
 *
 * @param <IN> a service specific type, representing a query of the service, which can be replayed later
 * @param <OUT> the fingerprint result of a service query, suitable for checking up-to-date status
 * @param <SERVICE> the type of the service
 */
public interface ImplicitInputsProvidingService<IN, OUT, SERVICE> {

    SERVICE withImplicitInputRecorder(ImplicitInputRecorder registrar);

    boolean isUpToDate(IN in, @Nullable OUT oldValue);

}
