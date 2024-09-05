/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.api.NonNullApi;

import java.lang.invoke.MethodHandles;

/**
 * A handler for Groovy call sites, including Indy ones, which is used to replace the call sites of some calls at runtime,
 * in order to alter their behavior.
 */
@NonNullApi
public interface CallSiteDecorator {
    CallSite maybeDecorateGroovyCallSite(CallSite originalCallSite);

    java.lang.invoke.CallSite maybeDecorateIndyCallSite(java.lang.invoke.CallSite originalCallSite, MethodHandles.Lookup caller, String callType, String name, int flags);
}
