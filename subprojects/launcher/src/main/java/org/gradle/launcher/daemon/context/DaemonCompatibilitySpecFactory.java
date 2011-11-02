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
package org.gradle.launcher.daemon.context;

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.internal.Factory;

public class DaemonCompatibilitySpecFactory implements Factory<Spec<DaemonContext>> {

    private final DaemonContext desiredContext;
    
    public DaemonCompatibilitySpecFactory(DaemonContext desiredContext) {
        this.desiredContext = desiredContext;
    }
    
    @SuppressWarnings("unchecked")
    public Spec<DaemonContext> create() {
        return new AndSpec<DaemonContext>(
            new ExactSameJavaHome()
        );
    }
    
    // package scope for testing
    
    class ExactSameJavaHome implements Spec<DaemonContext> {
        public boolean isSatisfiedBy(DaemonContext potentialContext) {
            return potentialContext.getJavaHome().equals(desiredContext.getJavaHome());
        }
    }

}