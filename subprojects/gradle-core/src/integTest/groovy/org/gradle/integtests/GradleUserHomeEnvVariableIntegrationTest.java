/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests;

import org.gradle.integtests.fixtures.GradleDistribution;
import org.gradle.integtests.fixtures.GradleDistributionExecuter;
import org.gradle.util.WrapUtil;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(DistributionIntegrationTestRunner.class)
public class GradleUserHomeEnvVariableIntegrationTest  {
    @Rule
    public final GradleDistribution dist = new GradleDistribution();
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter();

    @Test
    public void canDefineGradleUserHomeViaEnvVariable() {
        // the actual testing is done in the build script.
        File projectDir = new File(dist.getSamplesDir(), "gradleUserHome");
        File gradleUserHomeDir = new File(dist.getSamplesDir(), "gradleUserHome/customUserHome");
        executer.setDisableTestGradleUserHome(true);
        executer.withEnvironmentVars(WrapUtil.toMap("GRADLE_USER_HOME", gradleUserHomeDir.getAbsolutePath())).
                inDirectory(projectDir).withTasks("checkGradleUserHomeViaSystemEnv").run();
    }

    @Test
    public void checkDefaultGradleUserHome() {
        // the actual testing is done in the build script.
        File projectDir = new File(dist.getSamplesDir(), "gradleUserHome");
        executer.setDisableTestGradleUserHome(true);
        executer.inDirectory(projectDir).withTasks("checkDefaultGradleUserHome").run();
    }

    @Ignore
    public void systemPropGradleUserHomeHasPrecedenceOverEnvVariable() {
        // the actual testing is done in the build script.
        File projectDir = new File(dist.getSamplesDir(), "gradleUserHome");
        File gradleUserHomeDir = new File(dist.getSamplesDir(), "gradleUserHome/customUserHome");
        File systemPropGradleUserHomeDir = new File(dist.getSamplesDir(), "gradleUserHome/systemPropCustomUserHome");
        executer.setDisableTestGradleUserHome(true);
        executer.withArguments("-Dgradle.user.home=" + systemPropGradleUserHomeDir.getAbsolutePath()).
                withEnvironmentVars(WrapUtil.toMap("GRADLE_USER_HOME", gradleUserHomeDir.getAbsolutePath())).
                inDirectory(projectDir).withTasks("checkSystemPropertyGradleUserHomeHasPrecedence").run();
    }

}