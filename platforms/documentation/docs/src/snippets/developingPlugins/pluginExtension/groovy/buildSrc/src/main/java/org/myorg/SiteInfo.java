package org.myorg;

import org.gradle.api.provider.Property;

// tag::snippet[]
abstract public class SiteInfo {

    abstract public Property<String> getWebsiteUrl();

    abstract public Property<String> getVcsUrl();
}
// end::snippet[]
