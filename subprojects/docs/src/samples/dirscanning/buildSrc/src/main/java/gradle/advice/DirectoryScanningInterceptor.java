
package gradle.advice;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import org.gradle.api.internal.file.collections.DirectoryFileTree;

public class DirectoryScanningInterceptor {
    public static void install() throws ClassNotFoundException {
        ByteBuddyAgent.install();

        ClassLoader targetClassLoader = DirectoryFileTree.class.getClassLoader();

        ByteBuddy byteBuddy = new ByteBuddy();

        // interceptor class must be injected to the same classloader as the target class that is intercepted
        for (Class<?> clazz : new Class<?>[]{CountDirectoryScans.class, CountCacheStats.class, CountCacheStats.Hits.class, CountCacheStats.Misses.class}) {
            makeAndLoad(byteBuddy.redefine(clazz), targetClassLoader);
        }

        makeAndLoad(byteBuddy.redefine(DirectoryFileTree.class)
                .visit(Advice.to(CountDirectoryScans.class).on(ElementMatchers.named("visitFrom"))), targetClassLoader);


        makeAndLoad(byteBuddy.redefine(targetClassLoader.loadClass("org.gradle.api.internal.changedetection.state.CachingTreeVisitor"))
                .visit(Advice.to(CountCacheStats.Hits.class).on(ElementMatchers.named("recordCacheHit"))
                        .method(ElementMatchers.named("recordCacheMiss"), Advice.to(CountCacheStats.Misses.class))
                ), targetClassLoader);
    }

    private static void makeAndLoad(DynamicType.Builder<?> builder, ClassLoader targetClassLoader) {
        builder.make().load(targetClassLoader, ClassReloadingStrategy.fromInstalledAgent());
    }
}
