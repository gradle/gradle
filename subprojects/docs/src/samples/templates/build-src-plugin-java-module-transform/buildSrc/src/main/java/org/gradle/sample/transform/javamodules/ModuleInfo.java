package org.gradle.sample.transform.javamodules;

import java.io.Serializable;
import java.util.List;

/**
 * Data class to hold the information that should be added as module-info.class to an existing Jar file.
 */
public class ModuleInfo implements Serializable {
    private String moduleName;
    private String moduleVersion;
    private List<String> exports;
    private List<String> requires;
    private List<String> requiresTransitive;

    ModuleInfo(String moduleName, String moduleVersion, List<String> exports, List<String> requires, List<String> requiresTransitive) {
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
        this.exports = exports;
        this.requires = requires;
        this.requiresTransitive = requiresTransitive;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public List<String> getExports() {
        return exports;
    }

    public List<String> getRequires() {
        return requires;
    }

    public List<String> getRequiresTransitive() {
        return requiresTransitive;
    }
}
