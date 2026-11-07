package com.winlator.xserver.events;

import com.winlator.xserver.Window;

/** FocusIn（type=9）：焦点进入窗口时，按 X11 核心协议投递给该窗口及其中间祖先。 */
public class FocusIn extends FocusEvent {
    public FocusIn(Detail detail, Mode mode, Window event) {
        super(9, detail, mode, event);
    }
}
