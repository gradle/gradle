/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.scripts;

/**
 * Marks a compiled script class whose instances the configuration cache is allowed to
 * "scrub" rather than reject: the script's own Project-independent state (user-declared
 * {@code val}/{@code var} fields) is serialized, while the bridge to the build model
 * ({@code Project}/{@code Settings}/{@code Gradle}, the script host and its services) is dropped
 * and replaced with broken stand-ins upon deserialization.
 *
 * <p>This is an opt-in refinement of {@link GradleScript}. Script types that do not carry this
 * marker keep the default behavior of being rejected outright by the configuration cache.
 *
 * <p>Introduced for <a href="https://github.com/gradle/gradle/issues/22879">#22879</a>.
 */
public interface ScrubbableScript extends GradleScript {

    /**
     * Marks a type whose instances must be scrubbed out of a {@link ScrubbableScript} rather than
     * serialized for CC — the script host and any other data that reaches live build state. When the
     * configuration cache scrubs a script, fields of such a type are severed: dropped and, if the
     * type is an interface, replaced with a broken stand-in that reports a clear problem when used
     * at execution time.
     *
     * <p>Living here (in core-api) lets the scrubbing codec recognize such fields by type rather
     * than by field name, without depending on the DSL module that declares the concrete type.
     */
    interface ScrubbedOut {
    }
}
