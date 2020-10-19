package org.myorg;

import org.gradle.api.provider.Property;

abstract public class CustomData {

    abstract public Property<String> getWebsiteUrl();

    abstract public Property<String> getVcsUrl();
}
