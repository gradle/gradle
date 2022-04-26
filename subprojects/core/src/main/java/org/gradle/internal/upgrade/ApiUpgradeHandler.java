package org.gradle.internal.upgrade;

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

public class ApiUpgradeHandler {
    private static ApiUpgradeHandler INSTANCE;

    private final ImmutableList<Replacement> replacements;

    public ApiUpgradeHandler(ImmutableList<Replacement> replacements) {
        this.replacements = replacements;
    }

    public void useInstance() {
        INSTANCE = this;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeReplacement(Object receiver, Object[] args, int methodReplacementIndex) {
        MethodReplacement<T> methodReplacement = (MethodReplacement<T>) INSTANCE.replacements.get(methodReplacementIndex);
        return methodReplacement.invokeReplacement(receiver, args);
    }

    public static void decorateCallSiteArray(CallSiteArray callSites) {
        for (CallSite callSite : callSites.array) {
            for (Replacement replacement : INSTANCE.replacements) {
                replacement.decorateCallSite(callSite).ifPresent(decorated ->
                    callSites.array[callSite.getIndex()] = decorated
                );
            }
        }
    }
}
