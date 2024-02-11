package org.myorg;

import org.gradle.api.provider.Property;

// tag::snippet[]
abstract public class BinaryRepositoryExtension {

    abstract public Property<String> getCoordinates();

    abstract public Property<String> getServerUrl();
}
// end::snippet[]
