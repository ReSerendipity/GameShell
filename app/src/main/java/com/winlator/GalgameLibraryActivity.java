package com.winlator;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 游戏库入口（P8「合并双 UI」/ TD-01：消掉平行实现的第二套列表）。
 *
 * <p>列表 / 筛选 / 最近游玩 / 选择模式 / 批量删除只有**一份**实现：
 * {@link GalgameHomeFragment}。本类只做宿主，保留同名入口，让
 * {@code MainActivity}、{@code ContainersFragment} 与外部的 {@code startActivity}
 * 照旧可用；它自己不再持有 {@code GridView}、不再写第二份
 * 「容器列表 + 索引条目」合并逻辑。
 *
 * <p>放在 {@code com.winlator} 顶层而不是 {@code com.winlator.galgame}：
 * CI 的 galgame 子树类型门禁（ci/stubs + javac）要求该子树自包含，不许反向依赖
 * 顶层热点类；宿主天然要引用 {@link GalgameHomeFragment}，所以登记进
 * {@code ci/hotspot-entries.txt} 与其它顶层入口同批编译。
 */
public class GalgameLibraryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new GalgameHomeFragment())
                    .commitNow();
        }
    }
}
