/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.plugin.use.PluginId;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URL;
import java.net.URLClassLoader;

import static org.gradle.internal.hash.HashUtil.createHash;
import static org.objectweb.asm.Opcodes.*;

/**
 * Plugin resolver for script plugins.
 */
public class ScriptPluginPluginResolver implements PluginResolver {

    private final ScriptPluginLoaderClassLoader pluginsLoader;

    public ScriptPluginPluginResolver(TextResourceLoader textResourceLoader, ClassLoaderScope targetScope) {
        pluginsLoader = new ScriptPluginLoaderClassLoader(textResourceLoader, targetScope.getExportClassLoader());
        targetScope.createChild("script-plugins-loaders").export(pluginsLoader);
    }

    public static String getDescription() {
        return "Script Plugins";
    }

    @Override
    public void resolve(ContextAwarePluginRequest pluginRequest, PluginResolutionResult result) {
        if (pluginRequest.getScript() == null) {
            // TODO:rbo remove this notFound from the list
            result.notFound(getDescription(), "only script plugin requests are supported by this source");
            return;
        }
        if (pluginRequest.getModule() != null) {
            throw new InvalidUserDataException("explicit artifact coordinates are not supported for script plugins applied using the plugins block");
        }
        if (pluginRequest.getVersion() != null) {
            throw new InvalidUserDataException("explicit version is not supported for script plugins applied using the plugins block");
        }
        if (!pluginRequest.isApply()) {
            throw new InvalidUserDataException("apply false is not supported for script plugins applied using the plugins block");
        }

        ScriptPluginImplementation scriptPluginImplementation = new ScriptPluginImplementation(pluginRequest, pluginsLoader);
        result.found(getDescription(), new SimplePluginResolution(scriptPluginImplementation));
    }

    private static class ScriptPluginImplementation implements PluginImplementation<Object> {

        private final ContextAwarePluginRequest pluginRequest;
        private final ScriptPluginLoaderClassLoader pluginsLoader;

        private Class<?> pluginLoaderClass;

        private ScriptPluginImplementation(ContextAwarePluginRequest pluginRequest, ScriptPluginLoaderClassLoader pluginsLoader) {
            this.pluginRequest = pluginRequest;
            this.pluginsLoader = pluginsLoader;
        }

        @Override
        public Class<?> asClass() {
            if (pluginLoaderClass == null) {
                pluginLoaderClass = pluginsLoader.defineScriptPluginLoaderClass(pluginRequest, getDisplayName());
            }
            return pluginLoaderClass;
        }

        @Override
        public boolean isImperative() {
            return true;
        }

        @Override
        public boolean isHasRules() {
            return false;
        }

        @Override
        public Type getType() {
            return Type.IMPERATIVE_CLASS;
        }

        @Override
        public String getDisplayName() {
            return "script plugin '" + pluginRequest.getRelativeScriptUri() + "'";
        }

        @Nullable
        @Override
        public PluginId getPluginId() {
            return pluginRequest.getId();
        }

        @Override
        public boolean isAlsoKnownAs(PluginId id) {
            return id.equals(getPluginId());
        }
    }

    private static class ScriptPluginLoaderClassLoader extends URLClassLoader {

        private static final Type OBJECT_TYPE = Type.getType(Object.class);
        private static final Type STRING_TYPE = Type.getType(String.class);
        private static final Type INJECT_TYPE = Type.getType(Inject.class);
        private static final Type PLUGIN_TYPE = Type.getType(Plugin.class);
        private static final Type SCRIPT_HANDLER_TYPE = Type.getType(ScriptHandler.class);
        private static final Type SCRIPT_PLUGIN_TYPE = Type.getType(ScriptPlugin.class);
        private static final Type SCRIPT_PLUGIN_FACTORY_TYPE = Type.getType(ScriptPluginFactory.class);

        private static final Type PROJECT_REGISTRY_TYPE = Type.getType(ProjectRegistry.class);
        private static final Type PROJECT_INTERNAL_TYPE = Type.getType(ProjectInternal.class);
        private static final String PROJECT_INTERNAL_REGISTRY_SIGNATURE = 'L' + PROJECT_REGISTRY_TYPE.getInternalName() + '<' + PROJECT_INTERNAL_TYPE.getDescriptor() + ">;";

        private static final Type VOID_NO_ARG_METHOD = Type.getMethodType(Type.VOID_TYPE);
        private static final Type TO_STRING_METHOD = Type.getMethodType(STRING_TYPE);
        private static final Type APPLY_TO_OBJECT_METHOD = Type.getMethodType(Type.VOID_TYPE, OBJECT_TYPE);

        private static final String SYNTHETIC_LOADER_PACKAGE_NAME = "org.gradle.plugin.use.resolve.internal.script";
        private static final String SYNTHETIC_LOADER_PACKAGE_PATH = SYNTHETIC_LOADER_PACKAGE_NAME.replace(".", "/");
        private static final String SYNTHETIC_LOADER_TYPE_SIGNATURE = OBJECT_TYPE.getDescriptor() + ';' + PLUGIN_TYPE.getDescriptor() + '<' + OBJECT_TYPE.getDescriptor() + ";>;";
        private static final String SYNTHETIC_LOADER_CLASSNAME_PREFIX = "ScriptPluginSyntheticPluginLoader_";
        private static final Type SYNTHETIC_LOADER_CTOR = Type.getMethodType(Type.VOID_TYPE, PROJECT_REGISTRY_TYPE, SCRIPT_HANDLER_TYPE, SCRIPT_PLUGIN_FACTORY_TYPE);
        private static final String SYNTHETIC_LOADER_CTOR_SIGNATURE = '(' + PROJECT_INTERNAL_REGISTRY_SIGNATURE + SCRIPT_HANDLER_TYPE.getDescriptor() + SCRIPT_PLUGIN_FACTORY_TYPE.getDescriptor() + ")V";

        private static final Type SCRIPT_PLUGIN_LOADER_TYPE = Type.getType(ScriptPluginPluginLoader.class);
        private static final Type SCRIPT_PLUGIN_LOADER_LOAD_METHOD = Type.getMethodType(SCRIPT_PLUGIN_TYPE,
            STRING_TYPE, STRING_TYPE, STRING_TYPE, PROJECT_REGISTRY_TYPE, SCRIPT_HANDLER_TYPE, SCRIPT_PLUGIN_FACTORY_TYPE);

        private static final String PROJECT_REGISTRY_FIELD_NAME = "projectRegistry";
        private static final String SCRIPT_HANDLER_FIELD_NAME = "scriptHandler";
        private static final String SCRIPT_PLUGIN_FACTORY_FIELD_NAME = "scriptPluginFactory";

        private final TextResourceLoader textResourceLoader;

        private ScriptPluginLoaderClassLoader(TextResourceLoader textResourceLoader, ClassLoader parentLoader) {
            super(new URL[0], parentLoader);
            this.textResourceLoader = textResourceLoader;
        }

        private Class<?> defineScriptPluginLoaderClass(ContextAwarePluginRequest pluginRequest, String displayName) {

            String scriptContent = scriptContentFor(pluginRequest);
            String scriptContentHash = createHash(scriptContent, "SHA1").asCompactString();
            String classSimpleName = loaderClassSimpleNameFor(scriptContentHash);

            Class<?> syntheticLoaderClass = alreadyDefined(classSimpleName);
            if (syntheticLoaderClass != null) {
                // Don't redefine synthetic script plugin loader classes
                return syntheticLoaderClass;
            }

            String syntheticLoaderInternalName = SYNTHETIC_LOADER_PACKAGE_PATH + "/" + classSimpleName;

            ClassWriter cw = new ClassWriter(0);
            cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, syntheticLoaderInternalName, SYNTHETIC_LOADER_TYPE_SIGNATURE, OBJECT_TYPE.getInternalName(), new String[]{PLUGIN_TYPE.getInternalName()});

            defineInstanceMembers(cw);
            defineConstructor(cw, syntheticLoaderInternalName);
            defineApplyMethod(cw, syntheticLoaderInternalName, scriptContent, scriptContentHash, displayName);
            defineToStringMethod(cw, displayName);

            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            return defineClass(SYNTHETIC_LOADER_PACKAGE_NAME + "." + classSimpleName, bytes, 0, bytes.length);
        }

        private String scriptContentFor(ContextAwarePluginRequest pluginRequest) {
            return textResourceLoader.loadUri(pluginRequest.getDisplayName(), pluginRequest.getScriptUri()).getText();
        }

        @Nullable
        private Class<?> alreadyDefined(String classSimpleName) {
            String syntheticLoaderBinaryName = SYNTHETIC_LOADER_PACKAGE_NAME + "." + classSimpleName;
            try {
                return loadClass(syntheticLoaderBinaryName);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private void defineInstanceMembers(ClassWriter cw) {
            cw.visitField(ACC_PRIVATE + ACC_FINAL, PROJECT_REGISTRY_FIELD_NAME, PROJECT_REGISTRY_TYPE.getDescriptor(), PROJECT_INTERNAL_REGISTRY_SIGNATURE, null).visitEnd();
            cw.visitField(ACC_PRIVATE + ACC_FINAL, SCRIPT_HANDLER_FIELD_NAME, SCRIPT_HANDLER_TYPE.getDescriptor(), null, null).visitEnd();
            cw.visitField(ACC_PRIVATE + ACC_FINAL, SCRIPT_PLUGIN_FACTORY_FIELD_NAME, SCRIPT_PLUGIN_FACTORY_TYPE.getDescriptor(), null, null).visitEnd();
        }

        private void defineConstructor(ClassWriter cw, String syntheticLoaderInternalName) {
            MethodVisitor ctor = cw.visitMethod(ACC_PUBLIC, "<init>", SYNTHETIC_LOADER_CTOR.getInternalName(), SYNTHETIC_LOADER_CTOR_SIGNATURE, null);
            ctor.visitAnnotation(INJECT_TYPE.getDescriptor(), true).visitEnd();
            ctor.visitMaxs(2, 4);
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitMethodInsn(INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", VOID_NO_ARG_METHOD.getDescriptor(), false);
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 1);
            ctor.visitFieldInsn(PUTFIELD, syntheticLoaderInternalName, PROJECT_REGISTRY_FIELD_NAME, PROJECT_REGISTRY_TYPE.getDescriptor());
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 2);
            ctor.visitFieldInsn(PUTFIELD, syntheticLoaderInternalName, SCRIPT_HANDLER_FIELD_NAME, SCRIPT_HANDLER_TYPE.getDescriptor());
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 3);
            ctor.visitFieldInsn(PUTFIELD, syntheticLoaderInternalName, SCRIPT_PLUGIN_FACTORY_FIELD_NAME, SCRIPT_PLUGIN_FACTORY_TYPE.getDescriptor());
            ctor.visitInsn(RETURN);
            ctor.visitEnd();
        }

        private void defineApplyMethod(ClassWriter cw, String syntheticLoaderInternalName, String scriptContent, String scriptContentHash, String displayName) {
            MethodVisitor apply = cw.visitMethod(ACC_PUBLIC, "apply", APPLY_TO_OBJECT_METHOD.getDescriptor(), null, null);
            apply.visitMaxs(7, 4);
            apply.visitTypeInsn(NEW, SCRIPT_PLUGIN_LOADER_TYPE.getInternalName());
            apply.visitInsn(DUP);
            apply.visitMethodInsn(INVOKESPECIAL, SCRIPT_PLUGIN_LOADER_TYPE.getInternalName(), "<init>", VOID_NO_ARG_METHOD.getDescriptor(), false);
            apply.visitVarInsn(ASTORE, 2);
            apply.visitVarInsn(ALOAD, 2);
            apply.visitLdcInsn(scriptContent);
            apply.visitLdcInsn(scriptContentHash);
            apply.visitLdcInsn(displayName);
            apply.visitVarInsn(ALOAD, 0);
            apply.visitFieldInsn(GETFIELD, syntheticLoaderInternalName, PROJECT_REGISTRY_FIELD_NAME, PROJECT_REGISTRY_TYPE.getDescriptor());
            apply.visitVarInsn(ALOAD, 0);
            apply.visitFieldInsn(GETFIELD, syntheticLoaderInternalName, SCRIPT_HANDLER_FIELD_NAME, SCRIPT_HANDLER_TYPE.getDescriptor());
            apply.visitVarInsn(ALOAD, 0);
            apply.visitFieldInsn(GETFIELD, syntheticLoaderInternalName, SCRIPT_PLUGIN_FACTORY_FIELD_NAME, SCRIPT_PLUGIN_FACTORY_TYPE.getDescriptor());
            apply.visitMethodInsn(INVOKEVIRTUAL, SCRIPT_PLUGIN_LOADER_TYPE.getInternalName(), "load", SCRIPT_PLUGIN_LOADER_LOAD_METHOD.getDescriptor(), false);
            apply.visitVarInsn(ASTORE, 3);
            apply.visitVarInsn(ALOAD, 3);
            apply.visitVarInsn(ALOAD, 1);
            apply.visitMethodInsn(INVOKEINTERFACE, SCRIPT_PLUGIN_TYPE.getInternalName(), "apply", APPLY_TO_OBJECT_METHOD.getDescriptor(), true);
            apply.visitInsn(RETURN);
            apply.visitEnd();
        }

        private void defineToStringMethod(ClassWriter cw, String string) {
            MethodVisitor toString = cw.visitMethod(ACC_PUBLIC, "toString", TO_STRING_METHOD.getDescriptor(), null, null);
            toString.visitMaxs(1, 1);
            toString.visitLdcInsn(string);
            toString.visitInsn(ARETURN);
            toString.visitEnd();
        }

        private String loaderClassSimpleNameFor(String scriptContentHash) {
            return SYNTHETIC_LOADER_CLASSNAME_PREFIX + scriptContentHash;
        }
    }
}
