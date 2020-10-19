package org.myorg;

import org.gradle.api.provider.Property;

abstract public class UrlVerifierExtension {

    abstract public Property<String> getUrl();
}
