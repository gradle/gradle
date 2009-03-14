package org.gradle.api.tasks.javadoc;


/**
 * This class is used to hold the information that can be provided to the javadoc executable via the -linkoffline
 * option.
 *
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
