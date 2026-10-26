package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.util.WeakHashMap;

public class KelirBottomBar extends FrameLayout {

    public static final int TAB_CHATS = 0;
    public static final int TAB_CONTACTS = 1;
    public static final int TAB_CALLS = 2;
    public static final int TAB_SETTINGS = 3;

    private static final int TAB_COUNT = 4;
    private static final int REFRESH_INTERVAL = 250;

    private static final WeakHashMap<View, Integer> originalPaddings = new WeakHashMap<>();

    private final LaunchActivity activity;
    private final LinearLayout bar;
    private final View divider;
    private final ImageView[] tabIcons = new ImageView[TAB_COUNT];
    private final TextView[] tabLabels = new TextView[TAB_COUNT];

    private BaseFragment insetFragment;
    private int currentInset = -1;

    public static void attach(LaunchActivity launchActivity) {
        View host = launchActivity.getActionBarLayout().getView();
        if (!(host instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) host;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof KelirBottomBar) {
                return;
            }
        }
        group.addView(new KelirBottomBar(launchActivity), new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    public static int inset() {
        return ExteraConfig.kelirBottomNav ? AndroidUtilities.dp(58) : 0;
    }

    public KelirBottomBar(LaunchActivity context) {
        super(context);
        activity = context;
        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        addView(bar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        divider = new View(context);
        bar.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(1)));

        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(tabs, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(57)));

        int[] drawables = {R.drawable.kelir_tab_chats, R.drawable.kelir_tab_contacts, R.drawable.kelir_tab_calls, R.drawable.kelir_tab_settings};
        String[] keys = {"Chats", "Contacts", "Calls", "Settings"};
        int[] resIds = {R.string.Chats, R.string.Contacts, R.string.Calls, R.string.Settings};
        for (int i = 0; i < TAB_COUNT; i++) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);

            ImageView icon = new ImageView(context);
            icon.setImageResource(drawables[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(icon, new LinearLayout.LayoutParams(AndroidUtilities.dp(21), AndroidUtilities.dp(21)));

            TextView label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setSingleLine(true);
            label.setText(LocaleController.getString(keys[i], resIds[i]));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = AndroidUtilities.dp(3);
            cell.addView(label, labelParams);

            final int index = i;
            cell.setOnClickListener(v -> activity.kelirSwitchTab(index));
            tabs.addView(cell, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));

            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
        refresh();
    }

    private int tabOf(BaseFragment fragment) {
        if (fragment instanceof DialogsActivity) {
            return TAB_CHATS;
        }
        if (fragment instanceof ContactsActivity) {
            return TAB_CONTACTS;
        }
        if (fragment instanceof CallLogActivity) {
            return TAB_CALLS;
        }
        if (fragment instanceof ProfileActivity) {
            return TAB_SETTINGS;
        }
        return -1;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(ticker);
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, REFRESH_INTERVAL);
        }
    };

    private void refresh() {
        INavigationLayout layout = activity.getActionBarLayout();
        BaseFragment fragment = layout == null ? null : layout.getLastFragment();
        int tab = ExteraConfig.kelirBottomNav ? tabOf(fragment) : -1;
        if (tab < 0) {
            applyInset(null);
            if (getVisibility() != GONE) {
                setVisibility(GONE);
            }
            return;
        }
        applyInset(fragment);
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        int active = Theme.getColor(Theme.key_actionBarTabActiveText);
        int inactive = Theme.getColor(Theme.key_actionBarTabUnactiveText);
        for (int i = 0; i < TAB_COUNT; i++) {
            int color = i == tab ? active : inactive;
            tabIcons[i].setColorFilter(color);
            tabLabels[i].setTextColor(color);
        }
    }

    private void applyInset(BaseFragment fragment) {
        if (insetFragment == fragment) {
            return;
        }
        insetFragment = fragment;
        int bottom = fragment == null ? 0 : inset();
        View root = fragment == null ? null : fragment.getFragmentView();
        if (root != null) {
            padScrollables(root, root, bottom);
        }
    }

    private void padScrollables(View root, View view, int bottom) {
        if (view instanceof ListView || view instanceof RecyclerView || view instanceof ScrollView) {
            Integer saved = originalPaddings.get(view);
            if (saved == null) {
                saved = view.getPaddingBottom();
                originalPaddings.put(view, saved);
            }
            if (view instanceof ListView) {
                ((ListView) view).setClipToPadding(false);
            } else if (view instanceof RecyclerView) {
                ((RecyclerView) view).setClipToPadding(false);
            } else {
                ((ScrollView) view).setClipToPadding(false);
            }
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom == 0 ? saved : saved + bottom);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                padScrollables(root, group.getChildAt(i), bottom);
            }
        }
    }
}
