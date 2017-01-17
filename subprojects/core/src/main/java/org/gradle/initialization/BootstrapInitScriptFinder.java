package org.gradle.initialization;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URI;
import java.util.Collection;

public class BootstrapInitScriptFinder implements InitScriptFinder {

    private final FileResolver resolver;
    private final File gradleUserHome;
    private final String bootstrapUrl;

    public BootstrapInitScriptFinder(File gradleUserHome, String bootstrapUrl, FileResolver resolver) {
        this.gradleUserHome = gradleUserHome;
        this.bootstrapUrl = bootstrapUrl;
        this.resolver = resolver;
    }

    public void findScripts(Collection<File> scripts) {
        if(bootstrapUrl != null) {
            URI scriptUri = resolver.resolveUri(bootstrapUrl);
            ScriptSource scriptSource = new UriScriptSource("init-script", scriptUri);
            if(scriptSource.getResource().getFile() != null) {
                scripts.add(scriptSource.getResource().getFile());
            } else {
                //TODO: Rebase off #1022
                String scriptText = scriptSource.getResource().getText();
                String scriptName = "init-cache/" + HashUtil.createHash(scriptText, "SHA1") + ".gradle";
                File scriptFile = new File(gradleUserHome, scriptName);
                if (!scriptFile.exists()) {
                    scriptFile.getParentFile().mkdirs();
                    GFileUtils.writeFile(scriptText, scriptFile);
                }
                scripts.add(scriptFile);
            }
        }
    }
}
