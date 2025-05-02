package org.myorg;

import org.gradle.api.provider.Property;

// tag::snippet[]
abstract public class ServerEnvironment {
    private final String name;

    @javax.inject.Inject
    public ServerEnvironment(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    abstract public Property<String> getUrl();
}
// end::snippet[]
