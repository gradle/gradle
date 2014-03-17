/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal;
import org.gradle.nativebinaries.toolchain.ConfigurableToolChain;
import org.gradle.nativebinaries.toolchain.GccTool;
import org.gradle.nativebinaries.toolchain.PlatformConfigurableToolChain;
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration;
import org.gradle.nativebinaries.toolchain.internal.*;
import org.gradle.nativebinaries.toolchain.internal.tools.*;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * A tool chain that has GCC semantics, where all platform variants are produced by varying the tool args.
 */
public abstract class AbstractGccCompatibleToolChain extends AbstractToolChain implements PlatformConfigurableToolChain, ConfigurableToolChain {
    private final ExecActionFactory execActionFactory;
    private final ToolSearchPath toolSearchPath;
    private final DefaultToolRegistry toolRegistry = new DefaultToolRegistry();

    private final List<ApplyablePlatformConfiguration> platformConfigs = new ArrayList<ApplyablePlatformConfiguration>();
    private int configInsertLocation;

    public AbstractGccCompatibleToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolSearchPath toolSearchPath) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.toolSearchPath = toolSearchPath;

        target(new ToolChainDefaultArchitecture());
        target(new Intel32Architecture());
        target(new Intel64Architecture());
        configInsertLocation = 0;
    }

    protected GccTool getTool(ToolType toolType) {
        return toolRegistry.getTool(toolType);
    }

    protected void registerTool(ToolType type, String defaultExecutable) {
        toolRegistry.register(type, defaultExecutable);
    }

    protected CommandLineToolSearchResult locate(ToolType type) {
        GccTool gccTool = getTool(type);
        return toolSearchPath.locate(type, gccTool.getExecutable());
    }

    public List<File> getPath() {
        return toolSearchPath.getPath();
    }

    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            toolSearchPath.path(resolve(path));
        }
    }

    protected void initTools(ToolChainAvailability availability) {
        for (ToolType type : ToolType.values()) {
            locate(type);
        }
        boolean found = false;
        for (ToolType type : Arrays.asList(ToolType.C_COMPILER, ToolType.CPP_COMPILER, ToolType.OBJECTIVEC_COMPILER, ToolType.OBJECTIVECPP_COMPILER)) {
            found |= locate(type).isAvailable();
        }
        if (!found) {
            availability.mustBeAvailable(locate(ToolType.C_COMPILER));
        }
    }

    @Deprecated
    public void addPlatformConfiguration(TargetPlatformConfiguration platformConfig) {
        target(new ApplyableTargetPlatformConfigurationAdapter(platformConfig));
    }

    public void target(Platform platform, Action<ConfigurableToolChain> action) {
        target(platform.getName(), action);
    }

    public void target(String platformName, Action<ConfigurableToolChain> action) {
        target(new ConfigurableToolChainActionAdapter(platformName, action));
    }

    void target(ApplyablePlatformConfiguration applyablePlatformConfiguration) {
        platformConfigs.add(configInsertLocation, applyablePlatformConfiguration);
        configInsertLocation++;
    }


    public PlatformToolChain select(Platform targetPlatform) {
        ApplyablePlatformConfiguration applyablePlatformConfiguration = getPlatformConfiguration(targetPlatform);
        ToolChainAvailability result = new ToolChainAvailability();
        if (applyablePlatformConfiguration == null) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
            return new UnavailablePlatformToolChain(result);
        }
        initTools(result);
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolChain(result);
        }

        // Target the tools for the platform
        ToolRegistry platformTools = new PlatformToolRegistry(toolRegistry, applyablePlatformConfiguration);
        return new GccPlatformToolChain(toolSearchPath, platformTools, execActionFactory, canUseCommandFile());
    }

    protected ApplyablePlatformConfiguration getPlatformConfiguration(Platform targetPlatform) {
        for (ApplyablePlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
    }

    protected boolean canUseCommandFile() {
        return true;
    }

    public GccTool getCppCompiler() {
        return getTool(ToolType.CPP_COMPILER);
    }

    public GccTool getCCompiler() {
        return getTool(ToolType.C_COMPILER);
    }

    // This is here to allow using this property from Groovy as `cCompiler`
    public GccTool getcCompiler() {
        return getTool(ToolType.C_COMPILER);
    }

    public GccTool getAssembler() {
        return getTool(ToolType.ASSEMBLER);
    }

    public GccTool getObjcCompiler() {
        return getTool(ToolType.OBJECTIVEC_COMPILER);
    }

    public GccTool getObjcppCompiler() {
        return getTool(ToolType.OBJECTIVECPP_COMPILER);
    }

    public GccTool getLinker() {
        return getTool(ToolType.LINKER);
    }

    public GccTool getStaticLibArchiver() {
        return getTool(ToolType.STATIC_LIB_ARCHIVER);
    }

    private static class ToolChainDefaultArchitecture implements ApplyablePlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && targetPlatform.getArchitecture() == ArchitectureInternal.TOOL_CHAIN_DEFAULT;
        }

        public ConfigurableToolChain apply(ConfigurableToolChain configurableToolChain) {
            return configurableToolChain;
        }
    }

    private static class Intel32Architecture implements ApplyablePlatformConfiguration{

        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isI386();
        }

        public ConfigurableToolChain apply(ConfigurableToolChain configurableToolChain) {
            Action<ConfigurableToolChain> action = new Action<ConfigurableToolChain>() {
                public void execute(ConfigurableToolChain configurableToolChain) {
                    Action<List<String>> m32args = new Action<List<String>>() {
                        public void execute(List<String> args) {
                            args.add("-m32");
                        }
                    };
                    configurableToolChain.getCppCompiler().withArguments(m32args);
                    configurableToolChain.getCCompiler().withArguments(m32args);
                    configurableToolChain.getObjcCompiler().withArguments(m32args);
                    configurableToolChain.getObjcppCompiler().withArguments(m32args);
                    configurableToolChain.getLinker().withArguments(m32args);
                    configurableToolChain.getAssembler().withArguments(new Action<List<String>>() {
                        public void execute(List<String> args) {
                            if (OperatingSystem.current().isMacOsX()) {
                                args.addAll(asList("-arch", "i386"));
                            } else {
                                args.add("--32");
                            }
                        }
                    });
                }
            };
            action.execute(configurableToolChain);
            return configurableToolChain;
        }
    }

    private static class Intel64Architecture implements ApplyablePlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && !OperatingSystem.current().isWindows() // Currently don't support building 64-bit binaries on GCC/Windows
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isAmd64();
        }

        public ConfigurableToolChain apply(ConfigurableToolChain configurableToolChain) {
            Action<ConfigurableToolChain> action = new Action<ConfigurableToolChain>() {
                public void execute(ConfigurableToolChain configurableToolChain) {
                    Action<List<String>> m64ArgsAction = new Action<List<String>>() {
                        public void execute(List<String> args) {
                            args.add("-m64");
                        }
                    };
                    configurableToolChain.getCppCompiler().withArguments(m64ArgsAction);
                    configurableToolChain.getCCompiler().withArguments(m64ArgsAction);
                    configurableToolChain.getObjcCompiler().withArguments(m64ArgsAction);
                    configurableToolChain.getObjcppCompiler().withArguments(m64ArgsAction);
                    configurableToolChain.getLinker().withArguments(m64ArgsAction);
                    configurableToolChain.getAssembler().withArguments(new Action<List<String>>() {
                        public void execute(List<String> args) {
                            if (OperatingSystem.current().isMacOsX()) {
                               args.addAll(asList("-arch", "x86_64"));
                            } else {
                                args.add("--64");
                            }
                        }
                    });
                }
            };
            action.execute(configurableToolChain);
            return configurableToolChain;
        }
    }


    private class ApplyableTargetPlatformConfigurationAdapter implements ApplyablePlatformConfiguration {

        private TargetPlatformConfiguration targetPlatformConfiguration;

        public ApplyableTargetPlatformConfigurationAdapter(TargetPlatformConfiguration targetPlatformConfiguration) {
            this.targetPlatformConfiguration = targetPlatformConfiguration;
        }

        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatformConfiguration.supportsPlatform(targetPlatform);
        }

        public ConfigurableToolChain apply(ConfigurableToolChain configurableToolChain) {
            configurableToolChain.getAssembler().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getAssemblerArgs());
                }
            });

            configurableToolChain.getCppCompiler().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getCppCompilerArgs());
                }
            });

            configurableToolChain.getCCompiler().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getCCompilerArgs());
                }
            });

            configurableToolChain.getStaticLibArchiver().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getStaticLibraryArchiverArgs());
                }
            });

            configurableToolChain.getLinker().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getLinkerArgs());
                }
            });

            configurableToolChain.getObjcCompiler().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getObjectiveCCompilerArgs());
                }
            });

            configurableToolChain.getObjcppCompiler().withArguments(new Action<List<String>>() {
                public void execute(List<String> args) {
                    args.addAll(targetPlatformConfiguration.getObjectiveCppCompilerArgs());
                }
            });
            return configurableToolChain;
        }
    }

    private class ConfigurableToolChainActionAdapter implements ApplyablePlatformConfiguration {

        //TODO this should be a container of platforms
        private final String platformName;
        private Action<ConfigurableToolChain> configurationAction;

        public ConfigurableToolChainActionAdapter(String targetPlatformName, Action<ConfigurableToolChain> configurationAction) {
            this.platformName = targetPlatformName;
            this.configurationAction = configurationAction;
        }

        public boolean supportsPlatform(Platform targetPlatform) {
            return platformName.equals(targetPlatform.getName());
        }

        public ConfigurableToolChain apply(ConfigurableToolChain configurableToolChain) {
            configurationAction.execute(configurableToolChain);
            return configurableToolChain;
        }
    }
}
