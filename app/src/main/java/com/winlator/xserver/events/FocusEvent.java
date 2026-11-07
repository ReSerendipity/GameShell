package com.winlator.xserver.events;

import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;
import com.winlator.xserver.Window;

import java.io.IOException;

/**
 * FocusIn / FocusOut 的公共部分（X11 核心协议 §FocusIn / §FocusOut）。
 *
 * <p>布局：CARD8 type、CARD8 detail、CARD16 sequence、WINDOW event、CARD8 mode，其后 15 字节未用。
 * 之前本 fork 的 xserver 只记录 {@code WindowManager.focusedWindow} 字段、从不下发这两个事件，
 * 于是 Wine 侧永远认为自己是「未聚焦」——键事件因此到不了客户程序（TD-13）。
 */
public abstract class FocusEvent extends Event {
    public enum Detail {ANCESTOR, VIRTUAL, INFERIOR, NONLINEAR, NONLINEAR_VIRTUAL, POINTER, POINTER_ROOT, NONE}
    public enum Mode {NORMAL, GRAB, UNGRAB}

    private final Detail detail;
    private final Mode mode;
    private final Window event;

    public FocusEvent(int code, Detail detail, Mode mode, Window event) {
        super(code);
        this.detail = detail;
        this.mode = mode;
        this.event = event;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(code);
            outputStream.writeByte((byte)detail.ordinal());
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(event != null ? event.id : 0);
            outputStream.writeByte((byte)mode.ordinal());
            outputStream.writePad(15);
        }
    }
}
