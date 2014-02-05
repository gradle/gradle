package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.api.tasks.WorkResult;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.OptionsFileArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.SingleSourceCompileArgTransformer;

import java.io.File;

abstract public class NativeCompiler<T extends NativeCompileSpec> implements org.gradle.api.internal.tasks.compile.Compiler<T> {

    private final CommandLineTool<T> commandLineTool;
    private final OptionsFileArgsTransformer<T> argsTransFormer;

    NativeCompiler(CommandLineTool<T> commandLineTool, ArgsTransformer<T> argsTransFormer) {
        this.argsTransFormer = new OptionsFileArgsTransformer<T>(
                        ArgWriter.windowsStyleFactory(),
                        argsTransFormer);
        this.commandLineTool = commandLineTool;
    }

    public WorkResult execute(T spec) {
        boolean didWork = false;
        for (File sourceFile : spec.getSourceFiles()) {
            String objectFileName = FilenameUtils.removeExtension(sourceFile.getName()) + ".obj";
            WorkResult result = commandLineTool.inWorkDirectory(spec.getObjectFileDir())
                    .withArguments(new SingleSourceCompileArgTransformer<T>(sourceFile, objectFileName, argsTransFormer, true))
                    .execute(spec);
            didWork = didWork || result.getDidWork();
        }
        return new SimpleWorkResult(didWork);
    }
}
