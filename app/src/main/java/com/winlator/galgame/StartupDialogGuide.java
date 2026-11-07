package com.winlator.galgame;

/**
 * 启动期「窗口尺寸确认」对话框的推荐档位（TD-15 的 UX 层另一半：解决「不知道该选哪档」）。
 *
 * <p>判据只有一条：**在容器分辨率之内取最大档**——容器是硬上限，超了就会被算成
 * 裁切（TD-14 家族）；满足这条的前提下取面积最大的一档，画面最清楚。
 *
 * <p>为什么单独立一个类：TD-15 原文的处置是「在导入指引里建议选一个不大于容器
 * 分辨率的尺寸」，但纯文档给的是一条口号，用户站在对话框前仍要自己换算。这里把
 * 「哪一档」算出来，由 {@link GalgameDialogAutoClick} 在候选对话框出现、且该尺寸
 * 尚无教学点时提示一次。
 *
 * <p>纯计算，不引用 android.*：行为可用 javac 直接编译本文件跑断言（见
 * docs/tech-debt-register.md 的 TD-15 验证记录），不必起模拟器。
 */
public final class StartupDialogGuide {

    /** KiriKiri（krkrz）尺寸对话框的常见档位，按面积升序排列。 */
    private static final int[][] COMMON_SIZES = {
            {640, 480},
            {800, 600},
            {1024, 768},
            {1152, 864},
            {1280, 720},
            {1280, 800},
            {1280, 960},
            {1366, 768},
            {1440, 900},
            {1600, 900},
            {1920, 1080},
    };

    private StartupDialogGuide() {}

    /**
     * 推荐档位：容器之内面积最大的一档。
     *
     * @return {@code {width, height}}；容器尺寸非法（非正数）或连最小档都装不下时返回
     *         {@code null}——调用方不得在这种情况下编造推荐值。
     */
    public static int[] recommend(int containerWidth, int containerHeight) {
        if (containerWidth <= 0 || containerHeight <= 0) return null;
        int[] best = null;
        for (int[] size : COMMON_SIZES) {
            if (size[0] > containerWidth || size[1] > containerHeight) continue;
            if (best == null || size[0] * size[1] > best[0] * best[1]) best = size;
        }
        return best == null ? null : new int[]{best[0], best[1]};
    }

    /**
     * 解析容器的 {@code screenSize}（形如 {@code "1280x720"}）。
     *
     * @return {@code {width, height}}；格式不符或非正数返回 {@code null}。
     */
    public static int[] parseContainerSize(String screenSize) {
        if (screenSize == null) return null;
        int at = screenSize.indexOf('x');
        if (at <= 0 || at >= screenSize.length() - 1) return null;
        try {
            int w = Integer.parseInt(screenSize.substring(0, at).trim());
            int h = Integer.parseInt(screenSize.substring(at + 1).trim());
            return recommend(w, h);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code {1280, 720}} → {@code "1280x720"}；null 返回 null（不编造）。 */
    public static String format(int[] size) {
        return size == null ? null : size[0] + "x" + size[1];
    }

    /**
     * 提示文案里要填的推荐值：容器装得下就报具体档位，装不下就如实说「容器偏小」。
     *
     * @return 永不返回 null，直接可填空。
     */
    public static String describeRecommendation(String containerScreenSize, String fallbackText) {
        String formatted = format(parseContainerSize(containerScreenSize));
        return formatted != null ? formatted : fallbackText;
    }
}
