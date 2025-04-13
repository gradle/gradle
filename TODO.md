# TODO

## IDE Templates

```text
[KOTLIN_SCRIPTING] Cannot load script definition class org.gradle.kotlin.dsl.KotlinInitScript

java.lang.reflect.InvocationTargetException
	at java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance(DirectConstructorHandleAccessor.java:74)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:502)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:486)
	at kotlin.script.experimental.host.ConfigurationFromTemplateKt.constructCompilationConfiguration(configurationFromTemplate.kt:230)
	at kotlin.script.experimental.host.ConfigurationFromTemplateKt.createScriptDefinitionFromTemplate(configurationFromTemplate.kt:42)
	at kotlin.script.experimental.host.ConfigurationFromTemplateKt.createScriptDefinitionFromTemplate$default(configurationFromTemplate.kt:29)
	at org.jetbrains.kotlin.scripting.definitions.ScriptDefinition$FromTemplate.<init>(ScriptDefinition.kt:204)
	at org.jetbrains.kotlin.idea.core.script.ScriptDefinitionLoadingKt.loadDefinitionsFromTemplatesByPaths(scriptDefinitionLoading.kt:88)
	at org.jetbrains.kotlin.idea.gradleJava.ScriptDefinitionsUtilsKt.loadGradleTemplates(scriptDefinitionsUtils.kt:131)
	at org.jetbrains.kotlin.idea.gradleJava.ScriptDefinitionsUtilsKt.loadGradleDefinitions(scriptDefinitionsUtils.kt:50)
	at org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor.loadGradleDefinitions(GradleScriptDefinitionsProvider.kt:110)
	at org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor.getDefinitions(GradleScriptDefinitionsProvider.kt:162)
	at org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager.safeGetDefinitions(ScriptDefinitionsManager.kt:227)
	at org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager.reloadDefinitionsInternal(ScriptDefinitionsManager.kt:189)
	at org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager.getOrLoadDefinitions(ScriptDefinitionsManager.kt:176)
	at org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager.findDefinition(ScriptDefinitionsManager.kt:104)
	at org.jetbrains.kotlin.scripting.definitions.DefinitionsKt.findScriptDefinition(definitions.kt:67)
	at org.jetbrains.kotlin.scripting.definitions.DefinitionsKt.findScriptDefinition(definitions.kt:61)
	at org.jetbrains.kotlin.idea.base.analysis.RootKindMatcherImpl.matches(RootKindMatcherImpl.kt:64)
	at org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher$Companion.matches(RootKindFilter.kt:128)
	at org.jetbrains.kotlin.idea.base.projectStructure.RootKindMatcher$Companion.matches(RootKindFilter.kt:139)
	at org.jetbrains.kotlin.idea.base.projectStructure.SourceKindFilterUtils.matches(RootKindFilter.kt:151)
	at org.jetbrains.kotlin.idea.base.highlighting.KotlinHighlightingUtils.shouldHighlightFile(KotlinHighlightingUtils.kt:50)
	at org.jetbrains.kotlin.idea.highlighter.KotlinProblemHighlightFilter.shouldHighlight(KotlinProblemHighlightFilter.kt:12)
	at com.intellij.codeInsight.daemon.ProblemHighlightFilter.shouldProcess(ProblemHighlightFilter.java:39)
	at com.intellij.codeInsight.daemon.ProblemHighlightFilter.shouldHighlightFile(ProblemHighlightFilter.java:29)
	at com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarImpl.instantiatePasses(TextEditorHighlightingPassRegistrarImpl.java:179)
	at com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter.lambda$createPasses$0(TextEditorBackgroundHighlighter.java:80)
	at com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.use(trace.kt:30)
	at com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter.createPasses(TextEditorBackgroundHighlighter.java:75)
	at com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter.createPassesForEditor(TextEditorBackgroundHighlighter.java:98)
	at com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter.createPassesForEditor(TextEditorBackgroundHighlighter.java:30)
	at com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.lambda$submitInBackground$31(DaemonCodeAnalyzerImpl.java:1365)
	at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction(AnyThreadWriteThreadingSupport.kt:314)
	at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction(AnyThreadWriteThreadingSupport.kt:262)
	at com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:864)
	at com.intellij.openapi.application.ReadAction.compute(ReadAction.java:66)
	at com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.lambda$submitInBackground$32(DaemonCodeAnalyzerImpl.java:1344)
	at io.opentelemetry.context.Context.lambda$wrap$1(Context.java:212)
	at com.intellij.openapi.progress.impl.CoreProgressManager.lambda$executeProcessUnderProgress$14(CoreProgressManager.java:674)
	at com.intellij.openapi.progress.impl.CoreProgressManager.registerIndicatorAndRun(CoreProgressManager.java:749)
	at com.intellij.openapi.progress.impl.CoreProgressManager.computeUnderProgress(CoreProgressManager.java:705)
	at com.intellij.openapi.progress.impl.CoreProgressManager.executeProcessUnderProgress(CoreProgressManager.java:673)
	at com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:79)
	at com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.submitInBackground(DaemonCodeAnalyzerImpl.java:1343)
	at com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.lambda$queuePassesCreation$26(DaemonCodeAnalyzerImpl.java:1308)
	at io.opentelemetry.context.Context.lambda$wrap$1(Context.java:212)
	at com.intellij.util.concurrency.ChildContext$runInChildContext$1.invoke(propagation.kt:103)
	at com.intellij.util.concurrency.ChildContext$runInChildContext$1.invoke(propagation.kt:103)
	at com.intellij.util.concurrency.ChildContext.runInChildContext(propagation.kt:109)
	at com.intellij.util.concurrency.ChildContext.runInChildContext(propagation.kt:103)
	at com.intellij.util.concurrency.ContextRunnable.run(ContextRunnable.java:27)
	at com.intellij.concurrency.JobLauncherImpl$VoidForkJoinTask$1.exec(JobLauncherImpl.java:266)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:507)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1491)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:2073)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:2035)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:187)
Caused by: java.lang.NoClassDefFoundError: org/gradle/api/HasImplicitReceiver
	at org.gradle.kotlin.dsl.KotlinDslStandaloneScriptCompilationConfiguration$1.invoke(KotlinDslStandaloneScriptCompilationConfiguration.kt:46)
	at org.gradle.kotlin.dsl.KotlinDslStandaloneScriptCompilationConfiguration$1.invoke(KotlinDslStandaloneScriptCompilationConfiguration.kt:32)
	at kotlin.script.experimental.api.ScriptCompilationConfiguration.<init>(scriptCompilation.kt:23)
	at kotlin.script.experimental.api.ScriptCompilationConfiguration.<init>(scriptCompilation.kt:25)
	at org.gradle.kotlin.dsl.KotlinDslStandaloneScriptCompilationConfiguration.<init>(KotlinDslStandaloneScriptCompilationConfiguration.kt:32)
	at org.gradle.kotlin.dsl.KotlinInitScriptTemplateCompilationConfiguration.<init>(KotlinInitScript.kt:34)
	at java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance(DirectConstructorHandleAccessor.java:62)
	... 57 more
Caused by: java.lang.ClassNotFoundException: org.gradle.api.HasImplicitReceiver
	at java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:593)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)
	... 64 more
```


## Update Wrapper

[](build-logic-commons/gradle-plugin/build.gradle.kts)
[](build-logic/packaging/src/main/kotlin/gradlebuild/instrumentation/transforms/InstrumentationMetadataTransform.kt)
