package com.facebook.react.views.toolbar.events;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

public class ToolbarSearchTextEvent extends Event<ToolbarClickEvent> {

    private static final String EVENT_NAME = "topSearchText";

    private final String mText;

    public ToolbarSearchTextEvent(int viewId, String text) {
        super(viewId);
        mText = text;
    }

    @Override
    public String getEventName() {
        return EVENT_NAME;
    }

    @Override
    public boolean canCoalesce() {
        return false;
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
        WritableMap event = new WritableNativeMap();
        event.putString("text", mText);
        rctEventEmitter.receiveEvent(getViewTag(), getEventName(), event);
    }

}
