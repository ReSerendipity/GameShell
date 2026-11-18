package com.winlator.xenvironment.components;

import com.winlator.galgame.GalgameLaunchSession;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xconnector.XConnectorEpoll;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XClientConnectionHandler;
import com.winlator.xserver.XClientRequestHandler;
import com.winlator.xserver.XServer;

public class XServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final XServer xServer;
    private final UnixSocketConfig socketConfig;
    /** P0-4：首帧判据 = X 侧窗口收到内容更新（不是「渲染器画了一帧」）。 */
    private WindowManager.OnWindowModificationListener windowListener;

    public XServerComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, new XClientConnectionHandler(xServer), new XClientRequestHandler());
        connector.setInitialInputBufferCapacity(4096);
        connector.setInitialOutputBufferCapacity(4096);
        connector.setCanReceiveAncillaryMessages(true);
        // TD-10：把 connector 的 killConnection 注入 XServer，让「事件写失败」也能踢连接
        // （此前事件路径 catch 后只打印，坏连接会一直留在 epoll 上继续被写）
        xServer.setConnectionKiller(connector::killConnection);
        connector.start();

        // P0-4：listen 成功 = X 连接可建立；窗口首次收到内容 = 首帧到达。
        // 无活动 galgame 会话时 mark() 是空操作，普通容器行为不变。
        GalgameLaunchSession.mark(GalgameLaunchSession.X_SERVER_READY);
        windowListener = new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                GalgameLaunchSession.markFirstFrame();
            }
        };
        xServer.windowManager.addOnWindowModificationListener(windowListener);
    }

    @Override
    public void stop() {
        // 先摘监听器，避免 XServer 关闭过程中还往已结束的会话里写阶段
        if (windowListener != null) {
            xServer.windowManager.removeOnWindowModificationListener(windowListener);
            windowListener = null;
        }
        if (connector != null) {
            // 先摘钩子再销毁 connector：destroy 之后 nativePtr 归零，killConnection 会静默不生效，
            // 残留的客户端引用继续指向已释放的 connector
            xServer.setConnectionKiller(null);
            connector.destroy();
            connector = null;
        }
    }

    public XServer getXServer() {
        return xServer;
    }
}
