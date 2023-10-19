package org.myorg;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

// tag::snippet[]
abstract public class LatestArtifactVersion extends DefaultTask {

    @Input
    abstract public Property<String> getCoordinates();

    @Input
    abstract public Property<String> getServerUrl();

    @TaskAction
    public void resolveLatestVersion() {
        System.out.println("Retrieving artifact " + getCoordinates().get() + " from " + getServerUrl().get());
        // issue HTTP call and parse response
    }
}
// end::snippet[]
