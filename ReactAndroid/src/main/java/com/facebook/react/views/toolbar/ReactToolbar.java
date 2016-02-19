/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.views.toolbar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import javax.annotation.Nullable;

import com.facebook.react.R;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.generic.GenericDraweeHierarchyBuilder;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.DraweeHolder;
import com.facebook.drawee.view.MultiDraweeHolder;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.image.QualityInfo;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.toolbar.events.ToolbarClickEvent;
import com.facebook.react.views.toolbar.events.ToolbarSearchCancelledEvent;
import com.facebook.react.views.toolbar.events.ToolbarSearchPressedEvent;
import com.facebook.react.views.toolbar.events.ToolbarSearchTextEvent;

import java.util.ArrayList;

/**
 * Custom implementation of the {@link Toolbar} widget that adds support for remote images in logo
 * and navigationIcon using fresco.
 */
public class ReactToolbar extends Toolbar implements SearchView.OnQueryTextListener, SearchView.OnCloseListener, View.OnClickListener {
  private static final String LOG_TAG = "ReactToolbar";

  private static final String PROP_ACTION_ICON = "icon";
  private static final String PROP_ACTION_SHOW = "show";
  private static final String PROP_ACTION_SHOW_WITH_TEXT = "showWithText";
  private static final String PROP_ACTION_TITLE = "title";
  private static final String PROP_ACTION_ISSEARCH = "isSearch";

  private static final String PROP_ICON_URI = "uri";
  private static final String PROP_ICON_WIDTH = "width";
  private static final String PROP_ICON_HEIGHT = "height";

  private final DraweeHolder mLogoHolder;
  private final DraweeHolder mNavIconHolder;
  private final DraweeHolder mOverflowIconHolder;
  private final MultiDraweeHolder<GenericDraweeHierarchy> mActionsHolder =
          new MultiDraweeHolder<>();

  private IconControllerListener mLogoControllerListener;
  private IconControllerListener mNavIconControllerListener;
  private IconControllerListener mOverflowIconControllerListener;

  private enum SearchState {
    HIDDEN,
    VISIBLE_FOCUSED,
    VISIBLE_NOT_FOCUSED
  };
  private SearchState mSearchState = SearchState.HIDDEN;
  private SearchView mSearch = null;
  private MenuItem mSearchMenuItem = null;
  private String mSearchText = null;
  private String mSearchPlaceholder = null;

  private Drawable mNavIconDrawable = null;
  private Drawable mNavIconOverride = null;
  private int mNavTintColor;

  /**
   * Attaches specific icon width & height to a BaseControllerListener which will be used to
   * create the Drawable
   */
  private abstract class IconControllerListener extends BaseControllerListener<ImageInfo> {

    private final DraweeHolder mHolder;

    private IconImageInfo mIconImageInfo;

    public IconControllerListener(DraweeHolder holder) {
      mHolder = holder;
    }

    public void setIconImageInfo(IconImageInfo iconImageInfo) {
      mIconImageInfo = iconImageInfo;
    }

    @Override
    public void onFinalImageSet(String id, @Nullable ImageInfo imageInfo, @Nullable Animatable animatable) {
      super.onFinalImageSet(id, imageInfo, animatable);

      final ImageInfo info = mIconImageInfo != null ? mIconImageInfo : imageInfo;
      setDrawable(new DrawableWithIntrinsicSize(mHolder.getTopLevelDrawable(), info));
    }

    protected abstract void setDrawable(Drawable d);

  }

  private class ActionIconControllerListener extends IconControllerListener {
    private final MenuItem mItem;
    private final ReactToolbar mToolbar;

    ActionIconControllerListener(MenuItem item, DraweeHolder holder, ReactToolbar toolbar) {
      super(holder);
      mItem = item;
      mToolbar = toolbar;
    }

    @Override
    protected void setDrawable(Drawable d) {
      d.setColorFilter(mToolbar.getNavTintColor(), PorterDuff.Mode.SRC_IN);
      mItem.setIcon(d);
    }
  }

  /**
   * Simple implementation of ImageInfo, only providing width & height
   */
  private static class IconImageInfo implements ImageInfo {

    private int mWidth;
    private int mHeight;

    public IconImageInfo(int width, int height) {
      mWidth = width;
      mHeight = height;
    }

    @Override
    public int getWidth() {
      return mWidth;
    }

    @Override
    public int getHeight() {
      return mHeight;
    }

    @Override
    public QualityInfo getQualityInfo() {
      return null;
    }

  }

  public ReactToolbar(Context context, int defaultTintColor) {
    super(context);

    mLogoHolder = DraweeHolder.create(createDraweeHierarchy(), context);
    mNavIconHolder = DraweeHolder.create(createDraweeHierarchy(), context);
    mOverflowIconHolder = DraweeHolder.create(createDraweeHierarchy(), context);

    mLogoControllerListener = new IconControllerListener(mLogoHolder) {
      @Override
      protected void setDrawable(Drawable d) {
        setLogo(d);
      }
    };

    mNavIconControllerListener = new IconControllerListener(mNavIconHolder) {
      @Override
      protected void setDrawable(Drawable d) {
        mNavIconDrawable = d;
        updateNavIcon();
      }
    };

    mOverflowIconControllerListener = new IconControllerListener(mOverflowIconHolder) {
      @Override
      protected void setDrawable(Drawable d) {
        setOverflowIcon(d);
      }
    };

    mNavTintColor = defaultTintColor;
  }

  private final Runnable mLayoutRunnable = new Runnable() {
    @Override
    public void run() {
      measure(
          MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
      layout(getLeft(), getTop(), getRight(), getBottom());
    }
  };

  @Override
  public void requestLayout() {
    super.requestLayout();

    // The toolbar relies on a measure + layout pass happening after it calls requestLayout().
    // Without this, certain calls (e.g. setLogo) only take effect after a second invalidation.
    post(mLayoutRunnable);
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    detachDraweeHolders();
  }

  @Override
  public void onStartTemporaryDetach() {
    super.onStartTemporaryDetach();
    detachDraweeHolders();
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    attachDraweeHolders();
  }

  @Override
  public void onFinishTemporaryDetach() {
    super.onFinishTemporaryDetach();
    attachDraweeHolders();
  }

  private void detachDraweeHolders() {
    mLogoHolder.onDetach();
    mNavIconHolder.onDetach();
    mOverflowIconHolder.onDetach();
    mActionsHolder.onDetach();
  }

  private void attachDraweeHolders() {
    mLogoHolder.onAttach();
    mNavIconHolder.onAttach();
    mOverflowIconHolder.onAttach();
    mActionsHolder.onAttach();
  }

  /* package */ void setLogoSource(@Nullable ReadableMap source) {
    setIconSource(source, mLogoControllerListener, mLogoHolder);
  }

  /* package */ void setNavIconSource(@Nullable ReadableMap source) {
      setIconSource(source, mNavIconControllerListener, mNavIconHolder);
  }

  /* package */ void setOverflowIconSource(@Nullable ReadableMap source) {
    setIconSource(source, mOverflowIconControllerListener, mOverflowIconHolder);
  }

  /* package */ void setActions(@Nullable ReadableArray actions) {
    Menu menu = getMenu();
    menu.clear();
    mActionsHolder.clear();
    if (actions != null) {
      for (int i = 0; i < actions.size(); i++) {
        ReadableMap action = actions.getMap(i);

        MenuItem item = menu.add(Menu.NONE, Menu.NONE, i, action.getString(PROP_ACTION_TITLE));

        if (action.hasKey(PROP_ACTION_ICON)) {
          setMenuItemIcon(item, action.getMap(PROP_ACTION_ICON));
        }

        int showAsAction = action.hasKey(PROP_ACTION_SHOW)
            ? action.getInt(PROP_ACTION_SHOW)
            : MenuItem.SHOW_AS_ACTION_NEVER;
        if (action.hasKey(PROP_ACTION_SHOW_WITH_TEXT) &&
            action.getBoolean(PROP_ACTION_SHOW_WITH_TEXT)) {
          showAsAction = showAsAction | MenuItem.SHOW_AS_ACTION_WITH_TEXT;
        }
        item.setShowAsAction(showAsAction);

        boolean isSearch = action.hasKey(PROP_ACTION_ISSEARCH) ? action.getBoolean(PROP_ACTION_ISSEARCH) : false;
        if (isSearch) {
          mSearch = new SearchView(getContext());
          mSearch.setOnQueryTextListener(this);
          mSearch.setOnCloseListener(this);
          mSearch.setOnSearchClickListener(this);
          mSearchMenuItem = item;
          item.setActionView(mSearch);
        }
      }
    }

    updateMenuItemColor();
  }

  public void setNavTintColor(int color) {
    Log.e(LOG_TAG, "setting nav tint color: " + Integer.toHexString(color));
    mNavTintColor = color;
    updateNavIcon();
    updateMenuItemColor();
  }

  public int getNavTintColor() {
    return mNavTintColor;
  }

  private void updateNavIcon() {
    Drawable icon = mNavIconOverride != null ? mNavIconOverride : mNavIconDrawable;
    if (icon != null) {
      icon.setColorFilter(mNavTintColor, PorterDuff.Mode.SRC_IN);
      setNavigationIcon(icon);
    }
  }

  private void updateMenuItemColor() {
    Menu menu = getMenu();

    for (int i = 0; i < menu.size(); i++) {
      MenuItem item = menu.getItem(i);

      String title = item.getTitle().toString();
      ArrayList<View> items = new ArrayList<View>();
      findViewsWithText(items, title, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
      if (items.size() > 0) {
        ((TextView)items.get(0)).setTextColor(mNavTintColor);
      }

      Drawable icon = item.getIcon();
      if (icon != null) {
        icon.setColorFilter(mNavTintColor, PorterDuff.Mode.SRC_IN);
        item.setIcon(icon);
      }
    }
  }

  private void setMenuItemIcon(final MenuItem item, ReadableMap iconSource) {

    DraweeHolder<GenericDraweeHierarchy> holder =
            DraweeHolder.create(createDraweeHierarchy(), getContext());
    ActionIconControllerListener controllerListener = new ActionIconControllerListener(item, holder, this);
    controllerListener.setIconImageInfo(getIconImageInfo(iconSource));

    setIconSource(iconSource, controllerListener, holder);

    mActionsHolder.add(holder);

  }

  /**
   * Sets an icon for a specific icon source. If the uri indicates an icon
   * to be somewhere remote (http/https) or on the local filesystem, it uses fresco to load it.
   * Otherwise it loads the Drawable from the Resources and directly returns it via a callback
   */
  private void setIconSource(ReadableMap source, IconControllerListener controllerListener, DraweeHolder holder) {

    String uri = source != null ? source.getString(PROP_ICON_URI) : null;

    if (uri == null) {
      controllerListener.setIconImageInfo(null);
      controllerListener.setDrawable(null);
    } else if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("file://")) {
      controllerListener.setIconImageInfo(getIconImageInfo(source));
      DraweeController controller = Fresco.newDraweeControllerBuilder()
              .setUri(Uri.parse(uri))
              .setControllerListener(controllerListener)
              .setOldController(holder.getController())
              .build();
      holder.setController(controller);
      holder.getTopLevelDrawable().setVisible(true, true);
    } else {
      controllerListener.setDrawable(getDrawableByName(uri));
    }

  }

  private GenericDraweeHierarchy createDraweeHierarchy() {
    return new GenericDraweeHierarchyBuilder(getResources())
        .setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER)
        .setFadeDuration(0)
        .build();
  }

  private int getDrawableResourceByName(String name) {
    return getResources().getIdentifier(
        name,
        "drawable",
        getContext().getPackageName());
  }

  private Drawable getDrawableByName(String name) {
    int drawableResId = getDrawableResourceByName(name);
    if (drawableResId != 0) {
      return getResources().getDrawable(getDrawableResourceByName(name));
    } else {
      return null;
    }
  }

  private IconImageInfo getIconImageInfo(ReadableMap source) {
    if (source.hasKey(PROP_ICON_WIDTH) && source.hasKey(PROP_ICON_HEIGHT)) {
      final int width = Math.round(PixelUtil.toPixelFromDIP(source.getInt(PROP_ICON_WIDTH)));
      final int height = Math.round(PixelUtil.toPixelFromDIP(source.getInt(PROP_ICON_HEIGHT)));
      return new IconImageInfo(width, height);
    } else {
      return null;
    }
  }

  public void showSearch() {
    Log.d(LOG_TAG, "Show search");
    if (mSearchState == SearchState.HIDDEN) {
      setSearchState(SearchState.VISIBLE_NOT_FOCUSED);
    }
  }

  public void hideSearch() {
    Log.d(LOG_TAG, "Hide search");
    setSearchState(SearchState.HIDDEN);
  }

  private void hideKeyboard() {
    InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if(imm.isAcceptingText()) {
      imm.hideSoftInputFromWindow(mSearch.getWindowToken(), 0);
    }
  }

  private Drawable getDrawableFromId(int id) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return getResources().getDrawable(id, getContext().getTheme());
    }
    else {
      return getResources().getDrawable(id);
    }
  }

  private void setSearchState(SearchState state) {

    switch (state) {
      case HIDDEN:
        if (mSearchState != SearchState.HIDDEN) {
          hideKeyboard();
          mNavIconOverride = null;
          updateNavIcon();
          mSearchText = mSearch.getQuery().toString();
        }
        break;

      case VISIBLE_FOCUSED:
        mNavIconOverride = getDrawableFromId(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        updateNavIcon();
        mSearch.setQuery(mSearchText, false);
        mSearch.setQueryHint(mSearchPlaceholder);
        break;

      case VISIBLE_NOT_FOCUSED:
        switch (mSearchState) {
          case VISIBLE_FOCUSED:
            hideKeyboard();
            break;

          case HIDDEN:
            mSearch.setIconified(false);
            mSearch.clearFocus();
            mSearch.setQuery(mSearchText, false);
            mSearch.setQueryHint(mSearchPlaceholder);
            break;
        }
        break;
    }

    Log.d(LOG_TAG, "Change state " + mSearchState.name() + " -> " + state.name());
    mSearchState = state;
  }

  public void setSearchPrompt(@Nullable String prompt) {
  }

  public void setSearchPlaceholder(@Nullable String placeholder) {
    if (mSearch != null) {
      mSearch.setQueryHint(placeholder);
      mSearchPlaceholder = placeholder;
    }
  }

  public void setSearchText(@Nullable String text) {
    if (mSearch != null) {
      mSearch.setQuery(text, false);
      mSearchText = text;
    }
  }

  public void onNavClicked() {
    Log.d(LOG_TAG, "onNavClicked");

    if (mSearchState == SearchState.HIDDEN) {
      ReactContext reactContext = (ReactContext)getContext();
      EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
      eventDispatcher.dispatchEvent(new ToolbarClickEvent(getId(), SystemClock.uptimeMillis(), -1));
    }
    else {
      onClose();

      //setIconified(true) doesnt work unless the searchview is cleared and unfocused
      mSearch.setQuery(null, false);
      mSearch.clearFocus();
      mSearch.setIconified(true);
    }
  }

  public boolean onQueryTextSubmit(String query) {
    Log.d(LOG_TAG, "onQueryTextSubmit");

    ReactContext reactContext = (ReactContext)getContext();

    EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    ToolbarSearchPressedEvent event = new ToolbarSearchPressedEvent(getId(), SystemClock.uptimeMillis());
    eventDispatcher.dispatchEvent(event);

    setSearchState(SearchState.VISIBLE_NOT_FOCUSED);

    return false;
  }

  public boolean onQueryTextChange(String newText) {
    ReactContext reactContext = (ReactContext)getContext();

    EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    ToolbarSearchTextEvent event = new ToolbarSearchTextEvent(getId(), SystemClock.uptimeMillis(), newText);
    eventDispatcher.dispatchEvent(event);

    return true;
  }

  public boolean onClose() {
    Log.d(LOG_TAG, "onClose");

    ReactContext reactContext = (ReactContext)getContext();

    EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    ToolbarSearchCancelledEvent event = new ToolbarSearchCancelledEvent(getId(), SystemClock.uptimeMillis());
    eventDispatcher.dispatchEvent(event);

    setSearchState(SearchState.HIDDEN);

    return false;
  }

  public void onClick(View view) {
    Log.d(LOG_TAG, "onClick");

    ReactContext reactContext = (ReactContext)getContext();
    EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
    ToolbarClickEvent event = new ToolbarClickEvent(getId(), SystemClock.uptimeMillis(), mSearchMenuItem.getOrder());
    eventDispatcher.dispatchEvent(event);

    setSearchState(SearchState.VISIBLE_FOCUSED);
  }

}
