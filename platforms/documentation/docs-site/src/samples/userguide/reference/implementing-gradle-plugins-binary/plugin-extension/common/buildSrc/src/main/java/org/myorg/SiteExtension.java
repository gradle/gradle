package org.myorg;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Nested;

// tag::snippet[]
abstract public class SiteExtension {

    abstract public RegularFileProperty getOutputDir();

    @Nested
    abstract public SiteInfo getSiteInfo();

    public void siteInfo(Action<? super SiteInfo> action) {
        action.execute(getSiteInfo());
    }
}
// end::snippet[]
