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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import java.util.Objects;
import java.util.Set;

/**
 * A BuildSession-scoped service that reports JVM installation problems.
 *
 * <p>
 * This cannot be merged in to {@link DefaultJavaInstallationRegistry} as we must rediscover JVM installations on each build invocation,
 * as e.g. the {@code GradleBuild} task could be used to change the system properties and environment variables that affect the JVM installation.
 * </p>
 */
@NonNullApi
@ServiceScope(Scope.BuildSession.class)
public final class JvmInstallationProblemReporter {
    @NonNullApi
    private static final class ProblemReport {
        // Include auto-detection as it affects visibility of the problem. We do want to report twice if a location was auto-detected and then explicitly configured.
        private final boolean autoDetected;
        private final String problem;

        private ProblemReport(boolean autoDetected, String problem) {
            this.autoDetected = autoDetected;
            this.problem = problem;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProblemReport that = (ProblemReport) o;
            return autoDetected == that.autoDetected && Objects.equals(problem, that.problem);
        }

        @Override
        public int hashCode() {
            return Objects.hash(autoDetected, problem);
        }

        @Override
        public String toString() {
            return "ProblemReport{autoDetected=" + autoDetected + ", problem='" + problem + "'}";
        }
    }

    private final Set<ProblemReport> reportedProblems = Sets.newConcurrentHashSet();

    public void reportProblemIfNeeded(Logger targetLogger, InstallationLocation installationLocation, String message) {
        ProblemReport key = new ProblemReport(installationLocation.isAutoDetected(), message);
        if (!reportedProblems.add(key)) {
            return;
        }
        // If a user has explicitly configured a java installation, we should always log problems with it visibly, because they have bad configuration they can change.
        // But if we are just locating it automatically, we should log problems less visibly, because the user may be unable to fix the problem.
        targetLogger.log(key.autoDetected ? LogLevel.INFO : LogLevel.WARN, message);
    }
}
