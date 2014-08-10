/*
 * Copyright 2014 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;

public class DaemonUidCompatibilitySpec implements ExplainingSpec<DaemonContext> {

    private final ExplainingSpec<DaemonContext> delegateSpec;
    private final ExplainingSpec<String> uidSpec;

    public static ExplainingSpec<DaemonContext> createSpecRequiringUid(ExplainingSpec<DaemonContext> delegateSpec, String desiredUid) {
        return new DaemonUidCompatibilitySpec(delegateSpec, new UidRestrictionSpec(true, Collections.singleton(desiredUid)));
    }

    public static ExplainingSpec<DaemonContext> createSpecRejectingUids(ExplainingSpec<DaemonContext> delegateSpec, Collection<String> desiredUids) {
        return new DaemonUidCompatibilitySpec(delegateSpec, new UidRestrictionSpec(false, desiredUids));
    }

    private static class UidRestrictionSpec implements ExplainingSpec<String> {
        private final boolean required;
        private final Collection<String> uids;

        public UidRestrictionSpec(boolean required, Collection<String> uids) {
            this.required = required;
            this.uids = uids;
        }

        public String whyUnsatisfied(String uid) {
            if (!isSatisfiedBy(uid)) {
                return "UID restriction does not hold.\n" + description(uid);
            }
            return null;
        }

        private String description(String uid) {
            return (required ? "Required: " : "Rejected: ") + uids + "\n"
                    + "Actual: " + uid + "\n";
        }

        public boolean isSatisfiedBy(String uid) {
            return uids.contains(uid) == required;
        }
    }

    public DaemonUidCompatibilitySpec(ExplainingSpec<DaemonContext> delegateSpec, ExplainingSpec<String> uidSpec) {
        this.delegateSpec = delegateSpec;
        this.uidSpec = uidSpec;
    }

    public boolean isSatisfiedBy(DaemonContext potentialContext) {
        return delegateSpec.isSatisfiedBy(potentialContext) && uidSpec.isSatisfiedBy(potentialContext.getUid());
    }

    public String whyUnsatisfied(DaemonContext context) {
        String unsatisfiedDesc = uidSpec.whyUnsatisfied(context.getUid());
        if (unsatisfiedDesc != null) {
            return "Different daemon instance. " + unsatisfiedDesc;
        }
        return delegateSpec.whyUnsatisfied(context);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + delegateSpec.toString() + "," + uidSpec.toString() + "}";
    }
}