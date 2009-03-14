package org.gradle.api.tasks.javadoc;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class JavadocOfflineLink {
    private final String extDocUrl;
    private final String packagelistLoc;

    public JavadocOfflineLink(String extDocUrl, String packagelistLoc) {
        this.extDocUrl = extDocUrl;
        this.packagelistLoc = packagelistLoc;
    }

    public String getExtDocUrl() {
        return extDocUrl;
    }

    public String getPackagelistLoc() {
        return packagelistLoc;
    }
}
