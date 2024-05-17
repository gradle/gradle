package org.gradle.sample;

import org.gradle.sample.internal.ModuleBInternal;

public class ModuleB {
    private ModuleBInternal internal = new ModuleBInternal();

    protected String print() {
        String text = "text";
        return text;
    }
}
