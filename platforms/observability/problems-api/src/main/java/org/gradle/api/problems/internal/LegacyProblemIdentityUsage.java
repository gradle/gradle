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

package org.gradle.api.problems.internal;

/**
 * Reports a use of the deprecated problem identity API by code outside Gradle.
 * <p>
 * The deprecated getters and constructors live in this module, which the logging module depends on, so they cannot
 * nag themselves. Instead they report here, and the logging module installs a listener that turns each use into a
 * deprecation warning. Without a listener, a use is ignored.
 * <p>
 * Gradle's own code reads groups and ids through {@link ProblemGroupInternal#displayNameOf(org.gradle.api.problems.ProblemGroup)},
 * {@link ProblemGroupInternal#parentOf(org.gradle.api.problems.ProblemGroup)} and
 * {@link DefaultProblemId#displayNameOf(org.gradle.api.problems.ProblemId)}, which do not report.
 */
public final class LegacyProblemIdentityUsage {

    /**
     * A deprecated part of the problem identity API.
     */
    public enum Usage {
        GROUP_DISPLAY_NAME,
        GROUP_PARENT,
        ID_DISPLAY_NAME,
        GROUP_SUBCLASS,
        ID_SUBCLASS
    }

    /**
     * Receives the uses of the deprecated problem identity API.
     */
    public interface Listener {
        void used(Usage usage);
    }

    private static final Listener NONE = usage -> {};

    private static volatile Listener listener = NONE;

    private LegacyProblemIdentityUsage() {
    }

    public static void install(Listener newListener) {
        listener = newListener;
    }

    public static void uninstall() {
        listener = NONE;
    }

    public static void used(Usage usage) {
        listener.used(usage);
    }
}
