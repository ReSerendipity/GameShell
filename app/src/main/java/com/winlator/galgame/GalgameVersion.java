package com.winlator.galgame;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * TD-11：UI 里版本号的唯一来源 —— 直接取已安装 APK 的 {@code versionName}（源头是
 * {@code app/build.gradle} 的 {@code versionName}），不再在 strings / layout 里手抄。
 *
 * <p>为什么不用 {@code BuildConfig.VERSION_NAME}：本仓 AGP 8 默认关闭 buildconfig 生成
 * （{@code buildFeatures.buildConfig} 未声明），为一行版本号去开 BuildConfig 会给 CI 的
 * javac 类型门禁（对 {@code ci/stubs} 编译）多引一个需要额外打桩的生成类。
 * {@link PackageManager} 这条路不碰构建配置，与已安装包的实际版本天然一致。
 *
 * <p>取不到时返回 {@link #FALLBACK}，不让界面空着，也不伪造版本号。
 */
public final class GalgameVersion {
    /** 版本号取不到时的占位文案（正常安装包不会命中）。 */
    public static final String FALLBACK = "未知版本";

    private GalgameVersion() {}

    /** 返回已安装 APK 的 versionName（去首尾空白）；异常/缺失时返回 {@link #FALLBACK}。 */
    public static String versionName(Context context) {
        if (context == null) return FALLBACK;
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            String name = info == null ? null : info.versionName;
            if (name == null) return FALLBACK;
            name = name.trim();
            return name.isEmpty() ? FALLBACK : name;
        }
        catch (PackageManager.NameNotFoundException e) {
            return FALLBACK;
        }
    }
}
