# libxposed annotations are optional compile-time metadata.
-dontwarn io.github.libxposed.annotation.**

# Keep java_init.list synchronized when R8 obfuscates a module entry class.
-adaptresourcefilecontents META-INF/xposed/java_init.list

# The framework creates XposedModule entries reflectively through the no-arg constructor.
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
