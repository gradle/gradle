/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Lists;

import java.util.List;

public abstract class AbstractConfigurableMultiVersionSpecRunner extends AbstractMultiTestRunner {
    public static final String MULTI_VERSION_SYS_PROP = "org.gradle.integtest.multiversion";
    public static final String VERSIONS_SYSPROP_NAME = "org.gradle.integtest.versions";

    protected abstract void createExecutionsForContext(CoverageContext context);

    protected abstract void createSelectedExecutions(List<String> selectionCriteria);

    public AbstractConfigurableMultiVersionSpecRunner(Class<?> target) {
        super(target);
    }

    @Override
    protected void createExecutions() {
        String explicitVersions = System.getProperty(VERSIONS_SYSPROP_NAME);
        if (explicitVersions != null) {
            List<String> selectionCriteria = Lists.newArrayList(explicitVersions.split(","));
            createSelectedExecutions(selectionCriteria);
        } else {
            CoverageContext coverageContext = CoverageContext.from(System.getProperty(MULTI_VERSION_SYS_PROP, CoverageContext.DEFAULT.selector));
            createExecutionsForContext(coverageContext);
        }
    }

    protected enum CoverageContext {
        DEFAULT("default"), PARTIAL("partial"), FULL("all");

        final String selector;

        CoverageContext(String selector) {
            this.selector = selector;
        }

        static CoverageContext from(String requested) {
            for (CoverageContext context : values()) {
                if (context.selector == requested) {
                    return context;
                }
            }
            throw new IllegalArgumentException("There is no coverage context matching the string: ${requested}");
        }
    }
}
