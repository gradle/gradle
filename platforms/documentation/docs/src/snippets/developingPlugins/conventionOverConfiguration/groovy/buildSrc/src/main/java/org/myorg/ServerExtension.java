package org.myorg;

import org.gradle.api.provider.Property;

abstract public class ServerExtension {
    abstract public Property<String> getUrl();
}
