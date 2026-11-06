package com.winlator.xenvironment.components;

import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xconnector.XConnectorEpoll;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xserver.XClientConnectionHandler;
import com.winlator.xserver.XClientRequestHandler;
import com.winlator.xserver.XServer;

public class XServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final XServer xServer;
    private final UnixSocketConfig socketConfig;

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
    }

    @Override
    public void stop() {
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
