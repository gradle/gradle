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
package org.gradle.integtests.fixtures;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

public class GradleHandles implements MethodRule {

    private final GradleDistribution distribution;
    private final GradleDistributionExecuter executer;

    public GradleHandles() {
        this(new GradleDistribution());
    }

    public GradleHandles(GradleDistribution distribution) {
        this.distribution = distribution;
        this.executer = new GradleDistributionExecuter(GradleDistributionExecuter.Executer.forking, distribution);
    }

    public GradleDistributionExecuter getExecuter() {
        return this.executer;
    }

    public GradleHandle<? extends ForkingGradleExecuter> createHandle() {
        return executer.createHandle();
    }

    public Statement apply(final Statement base, final FrameworkMethod method, final Object target) {
        Statement doWithDistribution = new Statement() {
            public void evaluate() {
                executer.apply(base, method, target);
            }
        };

        return distribution.apply(doWithDistribution, method, target);
    }

}