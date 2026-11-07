package androidx.fragment.app;

/** CI 桩：androidx.fragment.app.FragmentTransaction 的最小子集（replace + commitNow）。 */
public abstract class FragmentTransaction {
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment);
    public abstract FragmentTransaction replace(int containerViewId, Fragment fragment, String tag);
    public abstract int commit();
    public abstract void commitNow();
}