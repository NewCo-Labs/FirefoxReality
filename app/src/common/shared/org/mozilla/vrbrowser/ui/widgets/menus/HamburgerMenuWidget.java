package org.mozilla.vrbrowser.ui.widgets.menus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.adapter.ComponentsAdapter;
import org.mozilla.vrbrowser.browser.engine.Session;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.databinding.HamburgerMenuBinding;
import org.mozilla.vrbrowser.ui.adapters.HamburgerMenuAdapter;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.AnimationHelper;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.browser.state.state.BrowserState;
import mozilla.components.browser.state.state.SessionState;
import mozilla.components.browser.state.state.WebExtensionState;
import mozilla.components.concept.engine.webextension.Action;

public class HamburgerMenuWidget extends UIWidget implements
        WidgetManagerDelegate.FocusChangeListener,
        ComponentsAdapter.StoreUpdatesListener {

    private boolean mProxify = SettingsStore.getInstance(getContext()).getLayersEnabled();

    public interface MenuDelegate {
        void onSendTab();
        void onResize();
        void onSwitchMode();
        void onAddons();
    }

    public static final int SWITCH_ITEM_ID = 0;

    private HamburgerMenuAdapter mAdapter;
    boolean mSendTabEnabled = false;
    private ArrayList<HamburgerMenuAdapter.MenuItem> mItems;
    private MenuDelegate mDelegate;

    public HamburgerMenuWidget(@NonNull Context aContext) {
        super(aContext);

        mItems = new ArrayList<>();

        updateUI();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        HamburgerMenuBinding binding = DataBindingUtil.inflate(inflater, R.layout.hamburger_menu, this, true);
        binding.setLifecycleOwner((VRBrowserActivity) getContext());
        mAdapter = new HamburgerMenuAdapter(getContext());
        binding.list.setAdapter(mAdapter);
        binding.list.setVerticalScrollBarEnabled(false);
        binding.list.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
        binding.list.addOnScrollListener(mScrollListener);
        binding.list.setHasFixedSize(true);
        binding.list.setItemViewCacheSize(20);
        binding.list.setDrawingCacheEnabled(true);
        binding.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        updateItems();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    public void show(int aShowFlags) {
        mWidgetPlacement.proxifyLayer = mProxify;
        super.show(aShowFlags);

        if (mWidgetManager != null) {
            mWidgetManager.addFocusChangeListener(this);
        }

        ComponentsAdapter.get().addStoreUpdatesListener(this);

        AnimationHelper.scaleIn(findViewById(R.id.menuContainer), 100, 0, null);
    }

    @Override
    public void hide(int aHideFlags) {
        AnimationHelper.scaleOut(findViewById(R.id.menuContainer), 100, 0, () -> HamburgerMenuWidget.super.hide(aHideFlags));
        mWidgetPlacement.proxifyLayer = false;

        if (mWidgetManager != null) {
            mWidgetManager.removeFocusChangeListener(this);
        }

        ComponentsAdapter.get().removeStoreUpdatesListener(this);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_width);
        aPlacement.parentAnchorX = 1.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 1.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationX = 20;
        aPlacement.translationY = 10;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.context_menu_z_distance);
    }

    public void setUAMode(int uaMode) {
        HamburgerMenuAdapter.MenuItem item = getSwitchModeIndex();
        if (item != null) {
            switch (uaMode) {
                case GeckoSessionSettings.USER_AGENT_MODE_DESKTOP: {
                    item.setIcon(R.drawable.ic_icon_ua_desktop);
                }
                break;

                case GeckoSessionSettings.USER_AGENT_MODE_MOBILE:
                case GeckoSessionSettings.USER_AGENT_MODE_VR: {
                    item.setIcon(R.drawable.ic_icon_ua_default);
                }
                break;

            }

            mAdapter.notifyItemChanged(mItems.indexOf(item));
        }
    }

    public void setMenuDelegate(@Nullable MenuDelegate delegate) {
        mDelegate = delegate;
    }

    private void updateItems() {
        mItems = new ArrayList<>();

        mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
        HamburgerMenuAdapter.MenuItem.TYPE_ADDONS_SETTINGS,
        (menuItem) -> {
            if (mDelegate != null) {
                mDelegate.onAddons();
            }
            return null;
        }).build());

        final Session activeSession = SessionStore.get().getActiveSession();
        String url = activeSession.getCurrentUri();
        boolean showAddons = (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) && !mWidgetManager.getFocusedWindow().isLibraryVisible();
        final SessionState tab = ComponentsAdapter.get().getSessionStateForSession(activeSession);
        if (tab != null && showAddons) {
            final List<WebExtensionState> extensions = ComponentsAdapter.get().getSortedEnabledExtensions();
            extensions.forEach((extension) -> {
                if (!extension.getAllowedInPrivateBrowsing() && activeSession.isPrivateMode()) {
                    return;
                }

                final WebExtensionState tabExtensionState = tab.getExtensionState().get(extension.getId());
                if (extension.getBrowserAction() != null) {
                    addOrUpdateAddonMenuItem(
                            extension,
                            extension.getBrowserAction(),
                            tabExtensionState != null ? tabExtensionState.getBrowserAction() : null);
                }
                if (extension.getPageAction() != null) {
                    addOrUpdateAddonMenuItem(
                            extension,
                            extension.getPageAction(),
                            tabExtensionState != null ? tabExtensionState.getPageAction() : null);
                }
            });
        }

        mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                (menuItem) -> {
                    if (mDelegate != null) {
                        mDelegate.onResize();
                    }
                    return null;
                })
                .withTitle(getContext().getString(R.string.hamburger_menu_resize))
                .withIcon(R.drawable.ic_icon_resize)
                .build());

        if (mSendTabEnabled) {
            mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                    (menuItem) -> {
                        if (mDelegate != null) {
                            mDelegate.onSendTab();
                        }
                        return null;
                    })
                    .withTitle(getContext().getString(R.string.hamburger_menu_send_tab))
                    .withIcon(R.drawable.ic_icon_tabs_sendtodevice)
                    .build());
        }

        mItems.add(new HamburgerMenuAdapter.MenuItem.Builder(
                HamburgerMenuAdapter.MenuItem.TYPE_DEFAULT,
                (menuItem) -> {
                    if (mDelegate != null) {
                        mDelegate.onSwitchMode();
                    }
                    return null;
                })
                .withId(SWITCH_ITEM_ID)
                .withTitle(getContext().getString(R.string.hamburger_menu_switch_to_desktop))
                .withIcon(R.drawable.ic_icon_ua_default)
                .build());

        mAdapter.setItems(mItems);
        mAdapter.notifyDataSetChanged();

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
        mWidgetPlacement.height += WidgetPlacement.dpDimension(getContext(), R.dimen.hamburger_menu_triangle_height);

        updateWidget();
    }

    private void addOrUpdateAddonMenuItem(final WebExtensionState extension,
                                          final @NonNull Action globalAction,
                                          final @Nullable Action tabAction
    ) {
        HamburgerMenuAdapter.MenuItem menuItem = mItems.stream().filter(item -> item.getAddonId().equals(extension.getId())).findFirst().orElse(null);
        if (menuItem == null) {
            menuItem = new HamburgerMenuAdapter.MenuItem.Builder(
                    HamburgerMenuAdapter.MenuItem.TYPE_ADDON,
                    (item) -> {
                        globalAction.getOnClick().invoke();
                        onDismiss();
                        return null;
                    })
                    .withAddonId(extension.getId())
                    .withTitle(extension.getName())
                    .withIcon(R.drawable.ic_icon_addons)
                    .withAction(globalAction)
            .build();
            mItems.add(menuItem);
        }
        if (tabAction != null) {
            menuItem.setAction(globalAction.copyWithOverride(tabAction));
        }
    }

    private HamburgerMenuAdapter.MenuItem getSwitchModeIndex() {
        return mItems.stream().filter(item -> item.getId() == SWITCH_ITEM_ID).findFirst().orElse(null);
    }

    public void setSendTabEnabled(boolean value) {
        mSendTabEnabled = value;
        updateItems();
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus) && isVisible()) {
            onDismiss();
        }
    }

    protected RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            if (recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_SETTLING) {
                recyclerView.requestFocus();
            }
        }
    };

    @Override
    public void onTabSelected(@NonNull BrowserState state, @Nullable mozilla.components.browser.state.state.SessionState tab) {
        updateItems();
    }

}
