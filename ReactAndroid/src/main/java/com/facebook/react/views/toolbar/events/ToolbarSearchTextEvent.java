/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.views.toolbar.events;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

public class ToolbarSearchTextEvent extends Event<ToolbarSearchTextEvent> {

    public static final String EVENT_NAME = "topSearchText";
    private final String mText;

    public ToolbarSearchTextEvent(int viewId, long timestampMs, String text) {
        super(viewId, timestampMs);
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
