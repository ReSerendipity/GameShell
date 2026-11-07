package androidx.fragment.app;

/** CI 桩：androidx.fragment.app.FragmentManager 的最小子集（仅 hosts 场景用到的入口）。 */
public abstract class FragmentManager {
    public abstract FragmentTransaction beginTransaction();
}