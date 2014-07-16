package org.gradle.nativebinaries.toolchain.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativebinaries.plugins.NativeComponentModelPlugin;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;
import org.gradle.nativebinaries.toolchain.internal.gcc.GccToolChain;
import org.gradle.process.internal.ExecActionFactory;

/**
 * A {@link Plugin} which makes the <a href="http://gcc.gnu.org/">GNU GCC/G++ compiler</a> available for compiling C/C++ code.
 */
@Incubating
public class GccCompilerPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getPlugins().apply(NativeComponentModelPlugin.class);
    }

    @RuleSource
    public static class Rules {
        @Mutate
        public static void addGccToolChain(ToolChainRegistryInternal toolChainRegistry, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final ExecActionFactory execActionFactory = serviceRegistry.get(ExecActionFactory.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);

            toolChainRegistry.registerFactory(Gcc.class, new NamedDomainObjectFactory<Gcc>() {
                public Gcc create(String name) {
                    return instantiator.newInstance(GccToolChain.class, instantiator, name, OperatingSystem.current(), fileResolver, execActionFactory);
                }
            });
            toolChainRegistry.registerDefaultToolChain(GccToolChain.DEFAULT_NAME, Gcc.class);
        }

    }
}
