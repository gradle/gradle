package org.gradle.sample.transform.javamodules;

import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A data class to collect all the module information we want to add.
 * Here the class is used as extension that can be configured in the build script
 * and as input to the ExtraModuleInfoTransform that add the information to Jars.
 */
public class ExtraModuleInfoPluginExtension implements TransformParameters, Serializable {

    private final Map<String, ModuleInfo> moduleInfo = new HashMap<>();
    private final Map<String, String> automaticModules = new HashMap<>();

    /**
     * Add full module information, including exported packages and dependencies, for a given Jar file.
     */
    public void addModuleInfo(String jarName, String moduleName, String moduleVersion, List<String> exports, List<String> requires, List<String> requiresTransitive) {
        moduleInfo.put(jarName, new ModuleInfo(moduleName, moduleVersion, exports, requires, requiresTransitive));
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void addAutomatic(String jarName, String moduleName) {
        automaticModules.put(jarName, moduleName);
    }

    @Input
    public Map<String, ModuleInfo> getModuleInfo() {
        return moduleInfo;
    }

    @Input
    public Map<String, String> getAutomaticModules() {
        return automaticModules;
    }
}
