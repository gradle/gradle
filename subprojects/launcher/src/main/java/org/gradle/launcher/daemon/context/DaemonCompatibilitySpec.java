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

import org.gradle.api.internal.specs.ExplainingSpec;

import static org.gradle.util.GFileUtils.canonicalise;

public class DaemonCompatibilitySpec implements ExplainingSpec<DaemonContext> {

    private final DaemonContext desiredContext;
    
    public DaemonCompatibilitySpec(DaemonContext desiredContext) {
        this.desiredContext = desiredContext;
    }

    public boolean isSatisfiedBy(DaemonContext potentialContext) {
        return whyUnsatisfied(potentialContext) == null;
    }

    public String whyUnsatisfied(DaemonContext context) {
        if (!javaHomeMatches(context)) {
            return "Java home is different.\n" + description(context);
        } else if (!daemonOptsMatch(context)) {
            return "At least one daemon option is different.\n" + description(context);
        }
        return null;
    }

    private String description(DaemonContext context) {
        return "Wanted: " + this + "\n"
                + "Actual: " + context + "\n";
    }

    private boolean daemonOptsMatch(DaemonContext potentialContext) {
        return potentialContext.getDaemonOpts().containsAll(desiredContext.getDaemonOpts())
                && potentialContext.getDaemonOpts().size() == desiredContext.getDaemonOpts().size();
    }

    private boolean javaHomeMatches(DaemonContext potentialContext) {
        return canonicalise(potentialContext.getJavaHome()).equals(canonicalise(desiredContext.getJavaHome()));
    }

    @Override
    public String toString() {
        return desiredContext.toString();
    }
}