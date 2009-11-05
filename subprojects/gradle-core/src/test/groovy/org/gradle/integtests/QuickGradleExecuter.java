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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;

import java.util.Map;
import java.util.HashMap;

public class QuickGradleExecuter extends AbstractGradleExecuter {
    private final GradleDistribution dist;
    private StartParameterModifier inProcessStartParameterModifier;
    private Map<String, String> environmentVars = new HashMap<String, String>();
    private String script = null;

    public QuickGradleExecuter(GradleDistribution dist) {
        this.dist = dist;
        inDirectory(dist.getTestDir());
    }

    @Override
    public GradleExecuter usingExecutable(String script) {
        this.script = script;
        return this;
    }

    public ExecutionResult run() {
        return configureExecuter().run();
    }

    public ExecutionFailure runWithFailure() {
        return configureExecuter().runWithFailure();
    }

    @Override
    public GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    public void setInProcessStartParameterModifier(StartParameterModifier inProcessStartParameterModifier) {
        this.inProcessStartParameterModifier = inProcessStartParameterModifier;
    }

    private GradleExecuter configureExecuter() {
        StartParameter parameter = new StartParameter();
        parameter.setLogLevel(LogLevel.INFO);
        parameter.setGradleHomeDir(dist.getGradleHomeDir());
        parameter.setGradleUserHomeDir(dist.getUserHomeDir());

        InProcessGradleExecuter inProcessGradleExecuter = new InProcessGradleExecuter(parameter);
        copyTo(inProcessGradleExecuter);

        GradleExecuter returnedExecuter = inProcessGradleExecuter; 

        if (inProcessGradleExecuter.getParameter().isShowVersion() || !environmentVars.isEmpty() || script != null) {
            ForkingGradleExecuter forkingGradleExecuter = new ForkingGradleExecuter(dist);
            copyTo(forkingGradleExecuter);
            forkingGradleExecuter.withEnvironmentVars(environmentVars);
            forkingGradleExecuter.usingExecutable(script);
            returnedExecuter = forkingGradleExecuter;
        } else {
            if (inProcessStartParameterModifier != null) {
                inProcessStartParameterModifier.modify(inProcessGradleExecuter.getParameter());
            }
        }

        return returnedExecuter;
    }

    public static interface StartParameterModifier {
        void modify(StartParameter startParameter);
    }
}
