/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativeplatform.fixtures;

import org.gradle.integtests.fixtures.AbstractMultiTestRunner;

import java.util.EnumSet;
import java.util.List;

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.*;

public class SingleToolChainTestRunner extends AbstractMultiTestRunner {
    private static final String TOOLCHAINS_SYSPROP_NAME = "org.gradle.integtest.cpp.toolChains";

    public SingleToolChainTestRunner(Class<? extends AbstractInstalledToolChainIntegrationSpec> target) {
        super(target);
    }

    @Override
    protected void createExecutions() {
        boolean enableAllToolChains = "all".equals(System.getProperty(TOOLCHAINS_SYSPROP_NAME, "default"));
        List<AvailableToolChains.ToolChainCandidate> toolChains = AvailableToolChains.getToolChains();
        if (enableAllToolChains) {
            for (AvailableToolChains.ToolChainCandidate toolChain : toolChains) {
                if (!toolChain.isAvailable()) {
                    throw new RuntimeException(String.format("Tool chain %s is not available.", toolChain.getDisplayName()));
                }
                add(new ToolChainExecution(toolChain, isRespectingSwiftConstraint(toolChain)));
            }
        } else {
            boolean hasEnabled = false;
            for (AvailableToolChains.ToolChainCandidate toolChain : toolChains) {
                if (!hasEnabled && toolChain.isAvailable() && isRespectingSwiftConstraint(toolChain)) {
                    add(new ToolChainExecution(toolChain, true));
                    hasEnabled = true;
                } else {
                    add(new ToolChainExecution(toolChain, false));
                }
            }
        }
    }

    private boolean isRespectingSwiftConstraint(AvailableToolChains.ToolChainCandidate toolChain) {
        RequiresInstalledToolChain toolChainRequirement = target.getAnnotation(RequiresInstalledToolChain.class);
        if (toolChainRequirement == null) {
            return true;
        }
        return EnumSet.of(SWIFT, SWIFT3, SWIFT4).contains(toolChainRequirement.value()) == toolChain instanceof AvailableToolChains.InstalledSwiftc;
    }

    private static class ToolChainExecution extends Execution {
        private final AvailableToolChains.ToolChainCandidate toolChain;
        private final boolean enabled;

        public ToolChainExecution(AvailableToolChains.ToolChainCandidate toolChain, boolean enabled) {
            this.toolChain = toolChain;
            this.enabled = enabled;
        }

        @Override
        protected String getDisplayName() {
            return toolChain.getDisplayName();
        }

        @Override
        protected boolean isTestEnabled(TestDetails testDetails) {
            if (enabled) {
                RequiresInstalledToolChain toolChainRestriction = testDetails.getAnnotation(RequiresInstalledToolChain.class);
                return toolChainRestriction == null
                        || toolChain.meets(toolChainRestriction.value());
            }
            return false;
        }

        @Override
        protected void assertCanExecute() {
            assert toolChain.isAvailable() : String.format("Tool chain %s not available", toolChain.getDisplayName());
        }

        @Override
        protected void before() {
            System.out.println(String.format("Using tool chain %s", toolChain.getDisplayName()));
            AbstractInstalledToolChainIntegrationSpec.setToolChain((AvailableToolChains.InstalledToolChain) toolChain);
        }
    }
}
