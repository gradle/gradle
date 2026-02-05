package org.example;

import org.gradle.api.provider.Property;

public interface MyExtension {
    Property<String> getFirstName();
    Property<String> getLastName();
}
