/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.util.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

public class Sample implements MethodRule {
    private final String name;
    private GradleDistribution dist;
    private TestFile sampleDir;

    public Sample(String name) {
        this.name = name;
    }

    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        dist = RuleHelper.getField(target, GradleDistribution.class);
        sampleDir = dist.getTestDir().file(name);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestFile srcDir = dist.getSamplesDir().file(name).assertIsDir();
                srcDir.copyTo(sampleDir);
                base.evaluate();
            }
        };
    }

    public TestFile getDir() {
        return sampleDir;
    }
}
