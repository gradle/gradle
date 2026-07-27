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

package org.gradle.test.preconditions

import org.gradle.integtests.fixtures.KillProcessAvailability
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.precondition.TestPrecondition

/**
 * Preconditions for the test execution context.
 * Checks executor mode (embedded, daemon, no-daemon), parallel execution, configuration cache,
 * isolated projects, and tooling that depends on the executor (MSBuild, S3 publishing, process killing).
 *
 * @see org.gradle.test.precondition
 */
class TestExecutionPreconditions {

    static final class IsLongLivingProcess implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isLongLivingProcess()
        }
    }

    static final class IsEmbeddedExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isEmbedded()
        }
    }

    static final class NotEmbeddedExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return TestPrecondition.notSatisfied(IsEmbeddedExecutor)
        }
    }

    static final class NotEmbeddedExecutorOrNotWindows implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return TestPrecondition.notSatisfied(IsEmbeddedExecutor) || TestPrecondition.notSatisfied(OsTestPreconditions.Windows)
        }
    }

    static final class IsDaemonOrNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isDaemon() || IntegrationTestBuildContext.isNoDaemon()
        }
    }

    static final class IsDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isDaemon()
        }
    }

    static final class NotDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !IntegrationTestBuildContext.isDaemon()
        }
    }

    static final class IsNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isNoDaemon()
        }
    }

    static final class NotNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return TestPrecondition.notSatisfied(IsNoDaemonExecutor)
        }
    }

    static final class IsParallelExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isParallel()
        }
    }

    static final class NotParallelExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !IntegrationTestBuildContext.isParallel()
        }
    }

    static final class NotParallelOrConfigCacheExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return TestPrecondition.notSatisfied(IsParallelExecutor) && TestPrecondition.notSatisfied(IsConfigCached)
        }
    }

    static final class IsConfigCached implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isConfigCache()
        }
    }

    static final class NotConfigCached implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isNotConfigCache()
        }
    }

    static final class IsolatedProjects implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isIsolatedProjects()
        }
    }

    static final class NotIsolatedProjects implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return IntegrationTestBuildContext.isNotIsolatedProjects()
        }
    }

    static final class HasMsBuild implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            // Simplistic approach at detecting MSBuild by assuming Windows imply MSBuild is present
            return TestPrecondition.satisfied(OsTestPreconditions.Windows) && TestPrecondition.notSatisfied(IsEmbeddedExecutor)
        }
    }

    static final class CanPublishToS3 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            // The S3 publish tests require the following
            return TestPrecondition.satisfied(JdkVersionTestPreconditions.Jdk9OrLater) || TestPrecondition.notSatisfied(IsEmbeddedExecutor)
        }
    }

    static class CanKillProcess implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return KillProcessAvailability.CAN_KILL
        }
    }
}
