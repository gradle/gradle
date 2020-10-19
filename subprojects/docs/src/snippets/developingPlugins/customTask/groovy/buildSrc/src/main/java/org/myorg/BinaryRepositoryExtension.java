package org.myorg;

import org.gradle.api.provider.Property;

abstract public class BinaryRepositoryExtension {

    abstract public Property<String> getCoordinates();

    abstract public Property<String> getServerUrl();
}
