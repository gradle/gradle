package org.gradle.api.publication.maven

import org.codehaus.groovy.runtime.InvokerHelper

class DefaultMavenPom implements MavenPom {
    void apply(Closure pomBuilder) {
        CustomModelBuilder modelBuilder = new CustomModelBuilder(getModel());
        InvokerHelper.invokeMethod(modelBuilder, "project", pomBuilder);
    }

    void whenConfigured(Closure modelTransformer) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    void withXml(Closure xmlBuilder) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
