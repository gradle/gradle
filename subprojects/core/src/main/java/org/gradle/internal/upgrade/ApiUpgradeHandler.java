/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade;

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

public class ApiUpgradeHandler {
    private static ApiUpgradeHandler instance;

    private final ImmutableList<Replacement> replacements;

    public ApiUpgradeHandler(ImmutableList<Replacement> replacements) {
        this.replacements = replacements;
    }

    public void useInstance() {
        instance = this;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeReplacement(Object receiver, Object[] args, int methodReplacementIndex) {
        MethodReplacement<T> methodReplacement = (MethodReplacement<T>) instance.replacements.get(methodReplacementIndex);
        return methodReplacement.invokeReplacement(receiver, args);
    }

    public static void decorateCallSiteArray(CallSiteArray callSites) {
        // TODO: It seems like for worker actions the instance may be null (different classloader)
        //       Though we should detect the situation and not silently ignore it.
        if (instance != null) {
            for (CallSite callSite : callSites.array) {
                for (Replacement replacement : instance.replacements) {
                    replacement.decorateCallSite(callSite).ifPresent(decorated ->
                        callSites.array[callSite.getIndex()] = decorated
                    );
                }
            }
        }
    }
}
