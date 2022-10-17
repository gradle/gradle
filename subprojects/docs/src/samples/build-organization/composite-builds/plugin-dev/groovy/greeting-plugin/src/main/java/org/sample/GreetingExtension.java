package org.sample;

import org.gradle.api.provider.Property;

public abstract class GreetingExtension {
    public abstract Property<String> getWho();
}
