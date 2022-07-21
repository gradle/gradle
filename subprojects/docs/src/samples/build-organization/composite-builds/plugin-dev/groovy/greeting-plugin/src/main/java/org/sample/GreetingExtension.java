package org.sample;

import org.gradle.api.provider.Property;

public abstract class GreetingExtension {

    public GreetingExtension() {
        getWho().convention("mate");
    }

    public abstract Property<String> getWho();
}
