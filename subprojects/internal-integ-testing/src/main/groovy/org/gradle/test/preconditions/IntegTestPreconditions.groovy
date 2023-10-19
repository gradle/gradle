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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.FeaturePreviewsActivationFixture
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.KillProcessAvailability
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.precondition.TestPrecondition
import org.gradle.util.internal.VersionNumber

class IntegTestPreconditions {

    static final class IsLongLivingProcess implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isLongLivingProcess()
        }
    }

    static final class IsEmbeddedExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isEmbedded()
        }
    }

    static final class NotEmbeddedExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return notSatisfied(IsEmbeddedExecutor)
        }
    }

    static final class NotEmbeddedExecutorOrNotWindows implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return notSatisfied(IsEmbeddedExecutor) || notSatisfied(UnitTestPreconditions.Windows)
        }
    }


    static final class IsDaemonOrNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isDaemon() || GradleContextualExecuter.isNoDaemon()
        }
    }

    static final class IsDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isDaemon()
        }
    }

    static final class NotDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !GradleContextualExecuter.isDaemon()
        }
    }

    static final class IsNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isNoDaemon()
        }
    }

    static final class NotNoDaemonExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return notSatisfied(IsNoDaemonExecutor)
        }
    }

    static final class IsParallelExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isParallel()
        }
    }

    static final class NotParallelExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !GradleContextualExecuter.isParallel()
        }
    }

    static final class NotParallelOrConfigCacheExecutor implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return notSatisfied(IsParallelExecutor) && notSatisfied(IsConfigCached)
        }
    }

    static final class IsConfigCached implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isConfigCache()
        }
    }

    static final class NotConfigCached implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isNotConfigCache()
        }
    }

    static final class NotIsolatedProjects implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return GradleContextualExecuter.isNotIsolatedProjects()
        }
    }

    static class Java7HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(7)
            )
        }
    }

    /**
     * A JVM that doesn't support running the current Gradle version is available.
     */
    static class UnsupportedJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !AvailableJavaHomes.getJdks(
                JavaVersion.toVersion(6),
                JavaVersion.toVersion(7)
            ).empty
        }
    }

    static class Java8HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(8)
            )
        }
    }

    static class MoreThanOneJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.availableJvms.size() >= 2
        }
    }

    static class MoreThanOneJavacAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.availableJdksWithJavac.size() >= 2
        }
    }

    static class MoreThanOneJava8HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getAvailableJdks(
                JavaVersion.toVersion(8)
            ).size() > 1
        }
    }

    static class HighestSupportedLTSJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getHighestSupportedLTS()
        }
    }

    static class LowestSupportedLTSJavaHomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getLowestSupportedLTS()
        }
    }

    static class Java11HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(11)
            )
        }
    }

    static class Java17HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(17)
            )
        }
    }

    static class Java18HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(18)
            )
        }
    }

    static class Java19HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(19)
            )
        }
    }

    static class Java20HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(20)
            )
        }
    }

    static class Java21HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(21)
            )
        }
    }

    static class Java22HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(22)
            )
        }
    }

    static class Java23HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(23)
            )
        }
    }

    static class Java24HomeAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getJdk(
                JavaVersion.toVersion(24)
            )
        }
    }

    static class BestJreAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.bestJre != null
        }
    }

    static class JavaHomeWithDifferentVersionAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.differentVersion != null
        }
    }

    static class Jdk17FromMultipleVendors implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.getAvailableJvmMetadatas().stream()
                .filter(metadata -> JavaVersion.VERSION_17 == metadata.languageVersion)
                .map {metadata -> metadata.vendor.rawVendor }
                .distinct()
                .count() >= 2
        }
    }

    static class DifferentJdkAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return AvailableJavaHomes.differentJdk != null
        }
    }

    static final class HasMsBuild implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            // Simplistic approach at detecting MSBuild by assuming Windows imply MSBuild is present
            return satisfied(UnitTestPreconditions.Windows) && notSatisfied(IsEmbeddedExecutor)
        }
    }

    static final class CanPublishToS3 implements TestPrecondition {
        @Override
        boolean isSatisfied() {
            // The S3 publish tests require the following
            return satisfied(UnitTestPreconditions.Jdk9OrLater) || notSatisfied(IsEmbeddedExecutor)
        }
    }

    static final class AgentInstrumentationDisabled implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !AbstractGradleExecuter.isAgentInstrumentationEnabled()
        }
    }

    static final class AnyActiveFeature implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return !FeaturePreviewsActivationFixture.inactiveFeatures().isEmpty()
        }
    }

    static class CanKillProcess implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return KillProcessAvailability.CAN_KILL
        }
    }

    static final class JavaRuntimeVersionSystemPropertyAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return System.getProperty('java.runtime.version') != null
        }
    }

    static final class Groovy3OrEarlier implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return VersionNumber.parse(GroovySystem.version).major <= 3
        }
    }
}
