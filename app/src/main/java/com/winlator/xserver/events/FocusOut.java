package com.winlator.xserver.events;

import com.winlator.xserver.Window;

/** FocusOut（type=10）：焦点离开某窗口时投递给该窗口及其祖先。 */
public class FocusOut extends FocusEvent {
    public FocusOut(Detail detail, Mode mode, Window event) {
        super(10, detail, mode, event);
    }
}
