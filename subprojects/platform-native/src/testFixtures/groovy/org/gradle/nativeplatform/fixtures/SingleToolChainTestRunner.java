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


import com.google.common.collect.Maps;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.AbstractConfigurableMultiVersionSpecRunner;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SingleToolChainTestRunner extends AbstractConfigurableMultiVersionSpecRunner {

    public SingleToolChainTestRunner(Class<? extends AbstractInstalledToolChainIntegrationSpec> target) {
        super(target);
    }

    @Override
    protected void createExecutionsForContext(CoverageContext context) {
        List<AvailableToolChains.ToolChainCandidate> toolChains = getAvailableToolChains(AvailableToolChains.getToolChains());

        switch(context) {
            case DEFAULT:
                add(new ToolChainExecution(toolChains.get(0)));
                break;
            case PARTIAL:
                for (AvailableToolChains.ToolChainCandidate toolChain : getLatestAvailableToolchainFromEachFamily(toolChains)) {
                    add(new ToolChainExecution(toolChain));
                }
                break;
            case FULL:
                for (AvailableToolChains.ToolChainCandidate toolChain : toolChains) {
                    add(new ToolChainExecution(toolChain));
                }
                break;
            default:
                throw new RuntimeException("Unhandled coverage context: " + context);
        }
    }

    @Override
    protected void createSelectedExecutions(List<String> selectionCriteria) {
        if (selectionCriteria.size() == 0 && selectionCriteria.get(0).equals("latest")) {
            List<AvailableToolChains.ToolChainCandidate> toolChains = getAvailableToolChains(AvailableToolChains.getToolChains());
            add(new ToolChainExecution(toolChains.get(0)));
        }
        throw new UnsupportedOperationException();
    }

    private List<AvailableToolChains.ToolChainCandidate> getAvailableToolChains(List<AvailableToolChains.ToolChainCandidate> toolChains) {
        return CollectionUtils.filter(toolChains, new Spec<AvailableToolChains.ToolChainCandidate>() {
            @Override
            public boolean isSatisfiedBy(AvailableToolChains.ToolChainCandidate toolChain) {
                return toolChain.isAvailable() && canUseToolChain(toolChain);
            }
        });
    }

    private Collection<AvailableToolChains.ToolChainCandidate> getLatestAvailableToolchainFromEachFamily(List<AvailableToolChains.ToolChainCandidate> toolChains) {
        Map<AvailableToolChains.ToolFamily, AvailableToolChains.ToolChainCandidate> availableByFamily = Maps.newEnumMap(AvailableToolChains.ToolFamily.class);
        for (AvailableToolChains.ToolChainCandidate toolChain : toolChains) {
            AvailableToolChains.ToolChainCandidate current = availableByFamily.get(toolChain.getFamily());
            if (current == null || current.getVersion().compareTo(toolChain.getVersion()) < 0) {
                availableByFamily.put(toolChain.getFamily(), toolChain);
            }
        }

        return availableByFamily.values();
    }

    @Override
    protected void createExecutions() {
        List<AvailableToolChains.ToolChainCandidate> toolChains = AvailableToolChains.getToolChains();

        for (AvailableToolChains.ToolChainCandidate toolChain : toolChains) {
            if (!toolChain.isAvailable()) {
                //throw new RuntimeException(String.format("Tool chain %s is not available.", toolChain.getDisplayName()));
                continue;
            }
            if (canUseToolChain(toolChain)) {
                add(new ToolChainExecution(toolChain));
            }
        }
    }

    // TODO: This exists because we detect all available native tool chains on a system (clang, gcc, swiftc, msvc).
    //
    // Many of our old tests assume that available tool chains can compile many/most languages, so they do not try to
    // restrict the required set of tool chains.
    //
    // The swiftc tool chain can build _only_ Swift, so tests that expect to use the swiftc tool chain properly annotate
    // their requirements with ToolChainRequirement.SWIFTC (or a version-specific requirement).
    //
    // Our multi-test runner is smart enough to disable tests that do not meet the test's requirements, but since many
    // of the old tests do not have requirements, we assume the tests require a "C" like tool chain (GCC, Clang, MSVC).
    //
    // In the future... we want to go back to old tests and annotate them with tool chains requirements.
    private boolean canUseToolChain(AvailableToolChains.ToolChainCandidate toolChain) {
        if (toolChain.meets(ToolChainRequirement.SWIFTC)) {
            RequiresInstalledToolChain toolChainRequirement = target.getAnnotation(RequiresInstalledToolChain.class);
            return toolChainRequirement!=null;
        }
        return true;
    }

    private static class ToolChainExecution extends Execution {
        private final AvailableToolChains.ToolChainCandidate toolChain;

        public ToolChainExecution(AvailableToolChains.ToolChainCandidate toolChain) {
            this.toolChain = toolChain;
        }

        @Override
        protected String getDisplayName() {
            return toolChain.getDisplayName();
        }

        @Override
        protected boolean isTestEnabled(TestDetails testDetails) {
            RequiresInstalledToolChain toolChainRestriction = testDetails.getAnnotation(RequiresInstalledToolChain.class);
            return toolChainRestriction == null || toolChain.meets(toolChainRestriction.value());
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
