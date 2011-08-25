/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testing

import org.gradle.util.TestFile
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter

/**
 * An experiment for a generally usable API for driving Gradle programmatically.
 * 
 * This should not be used.
 */
class GradleContext {

    @Delegate final GradleExecuter executer
    @Delegate final GradleDistribution distribution
    final TestFile dir

    GradleContext(GradleExecuter executer, GradleDistribution distribution, TestFile dir) {
        this.executer = executer;
        this.distribution = distribution;
        this.dir = dir;

        executer.inDirectory(dir)
    }

    static with(Map params = [:], Closure closure = null) {
        def distribution = determineDistribution(params.dist)
        def executer = determineExecuter(params.executer, distribution)
        def context = new GradleContext(executer, distribution, determineDir(params.dir))

        if (closure) {
            context.with(closure)
        } else {
            context
        }
    }

    static private determineDistribution(distParam) {
        // could select different versions here for
        new GradleDistribution()
    }

    static private determineExecuter(executerParam, GradleDistribution distribution) {
        if (executerParam) {
            new GradleDistributionExecuter(GradleDistributionExecuter.Executer.valueOf(executerParam.toString()), distribution)
        } else {
            new GradleDistributionExecuter(distribution)
        }
    }

    static private determineDir(dirParam) {
        if (dirParam == null) {
            new File()
        } else if (dirParam instanceof File) {
            dirParam
        } else {
            new File(dirParam.toString())
        }
    }
}