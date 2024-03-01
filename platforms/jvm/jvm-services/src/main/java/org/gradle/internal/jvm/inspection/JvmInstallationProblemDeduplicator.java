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

package org.gradle.internal.jvm.inspection;

import com.google.common.collect.Sets;
import org.gradle.api.NonNullApi;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Set;

/**
 * A BuildSession-scoped service that deduplicates JVM installation problems.
 *
 * <p>
 * This cannot be merged in to {@link JavaInstallationRegistry} as we must rediscover JVM installations on each build invocation,
 * as e.g. the {@code GradleBuild} task could be used to change the system properties and environment variables that affect the JVM installation.
 * </p>
 */
@NonNullApi
@ServiceScope(Scopes.BuildSession.class)
public final class JvmInstallationProblemDeduplicator {
    private final Set<String> reportedProblems = Sets.newConcurrentHashSet();

    public boolean shouldReportProblem(String problem) {
        return reportedProblems.add(problem);
    }
}
