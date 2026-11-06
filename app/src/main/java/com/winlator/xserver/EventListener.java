package com.winlator.xserver;

import com.winlator.core.Bitmask;
import com.winlator.xserver.events.Event;

import java.io.IOException;

public class EventListener {
    public final XClient client;
    public final Bitmask eventMask;

    public EventListener(XClient client, Bitmask eventMask) {
        this.client = client;
        this.eventMask = eventMask;
    }

    public boolean isInterestedIn(int eventId) {
        return eventMask.isSet(eventId);
    }

    public boolean isInterestedIn(Bitmask mask) {
        return this.eventMask.intersects(mask);
    }

    public void sendEvent(Event event) {
        try {
            event.send(client.getSequenceNumber(), client.getOutputStream());
        }
        catch (IOException e) {
            // TD-10：事件发送路径原先各自 catch 后吞掉异常，绕过了 XConnectorEpoll 里
            // 「IOException → killConnection」的既有策略；服务端会在半坏连接上继续发事件。
            client.killOnWriteFailure(e);
        }
    }
}
