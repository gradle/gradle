/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.jvm.Jvm

@TargetCoverage({ supportedVersionsByLowerJdk() })
class LowerToolchainZincScalaCompileIntegrationTest extends AbstractToolchainZincScalaCompileIntegrationTest {
    private static supportedVersionsByLowerJdk() {
        def jdk = jdkForTest()
        if (jdk == null) {
            return []
        }
        return ScalaCoverage.SUPPORTED_BY_JDK.intersect(ScalaCoverage.scalaVersionsSupportedByJdk(jdk.javaVersion))
    }

    private static jdkForTest() {
        AvailableJavaHomes.getAvailableJdk {
            it.languageVersion < Jvm.current().javaVersion && it.languageVersion.isJava8Compatible()
        }
    }

    @Override
    Jvm computeJdkForTest() {
        jdkForTest()
    }
}
