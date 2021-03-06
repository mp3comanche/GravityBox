/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.gm2.gravitybox;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XCallback;

public class ModBatteryStyle {
    private static final String TAG = "GB:ModBatteryStyle";
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    public static final String CLASS_BATTERY_CONTROLLER = "com.android.systemui.statusbar.policy.BatteryController";
    private static final boolean DEBUG = false;

    private static final String ACTION_MTK_BATTERY_PERCENTAGE_SWITCH = "mediatek.intent.action.BATTERY_PERCENTAGE_SWITCH";
    public static final String EXTRA_MTK_BATTERY_PERCENTAGE_STATE = "state";
    public static final String SETTING_MTK_BATTERY_PERCENTAGE = "battery_percentage";

    private static int mBatteryStyle;
    private static boolean mBatteryPercentText;
    private static boolean mMtkPercentText;
    private static Object mBatteryController;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED)) {
                if (intent.hasExtra("batteryStyle")) {
                    mBatteryStyle = intent.getIntExtra("batteryStyle", 1);
                    if (DEBUG) log("mBatteryStyle changed to: " + mBatteryStyle);
                }
                if (intent.hasExtra("batteryPercent")) {
                    mBatteryPercentText = intent.getBooleanExtra("batteryPercent", false);
                    if (DEBUG) log("mBatteryPercentText changed to: " + mBatteryPercentText);
                }
                updateBatteryStyle();
            } else if (intent.getAction().equals(ACTION_MTK_BATTERY_PERCENTAGE_SWITCH)) {
                mMtkPercentText = intent.getIntExtra(EXTRA_MTK_BATTERY_PERCENTAGE_STATE, 0) == 1;
                if (DEBUG) log("mMtkPercentText changed to: " + mMtkPercentText);
                updateBatteryStyle();
            }
        }
    };

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        try {
            String layout = Utils.hasGeminiSupport() ? "gemini_super_status_bar" : "super_status_bar";
            resparam.res.hookLayout(PACKAGE_NAME, "layout", layout, new XC_LayoutInflated() {

                @Override
                public void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {

                    ViewGroup vg = (ViewGroup) liparam.view.findViewById(
                            liparam.res.getIdentifier("signal_battery_cluster", "id", PACKAGE_NAME));

                    // inject percent text if it doesn't exist
                    TextView percText = (TextView) vg.findViewById(liparam.res.getIdentifier(
                            "percentage", "id", PACKAGE_NAME));
                    if (percText == null) {
                        percText = new TextView(vg.getContext());
                        percText.setTag("percentage");
                        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                        percText.setLayoutParams(lParams);
                        percText.setPadding(4, 0, 0, 0);
                        percText.setTextSize(1, 16);
                        percText.setTextColor(vg.getContext().getResources().getColor(
                                android.R.color.holo_blue_dark));
                        percText.setVisibility(View.GONE);
                        vg.addView(percText);
                        if (DEBUG) log("Battery percent text injected");
                    } else {
                        percText.setTag("percentage");
                    }
                    ModStatusbarColor.setPercentage(percText);

                    // GM2 specific - if there's already view with id "circle_battery", remove it
                    if (Build.DISPLAY.toLowerCase().contains("gravitymod")) {
                        ImageView exView = (ImageView) vg.findViewById(liparam.res.getIdentifier(
                                "circle_battery", "id", PACKAGE_NAME));
                        if (exView != null) {
                            if (DEBUG) log("GM2 circle_battery view found - removing");
                            vg.removeView(exView);
                        }
                    }

                    // inject circle battery view
                    CmCircleBattery circleBattery = new CmCircleBattery(vg.getContext());
                    circleBattery.setTag("circle_battery");
                    LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    circleBattery.setLayoutParams(lParams);
                    circleBattery.setPadding(4, 0, 0, 0);
                    circleBattery.setVisibility(View.GONE);
                    ModStatusbarColor.setCircleBattery(circleBattery);
                    vg.addView(circleBattery);
                    if (DEBUG) log("CmCircleBattery injected");

                    // find battery
                    ImageView battery = (ImageView) vg.findViewById(
                            liparam.res.getIdentifier("battery", "id", PACKAGE_NAME));
                    if (battery != null) {
                        battery.setTag("stock_battery");
                        ModStatusbarColor.setBattery(battery);
                    }
                }
                
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    public static void init(final XSharedPreferences prefs, ClassLoader classLoader) {

        if (DEBUG) log("init");

        try {

            Class<?> phoneStatusBarClass = findClass(CLASS_PHONE_STATUSBAR, classLoader);
            Class<?> batteryControllerClass = findClass(CLASS_BATTERY_CONTROLLER, classLoader);

            findAndHookMethod(phoneStatusBarClass, "makeStatusBarView", new XC_MethodHook(XCallback.PRIORITY_HIGHEST) {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                    Object mBatteryController = XposedHelpers.getObjectField(param.thisObject, "mBatteryController");
                    View mStatusBarView = (View) XposedHelpers.getObjectField(param.thisObject, "mStatusBarView");

                    ImageView circleBattery = (ImageView) mStatusBarView.findViewWithTag("circle_battery");
                    if (circleBattery != null) {
                        XposedHelpers.callMethod(mBatteryController, "addIconView", circleBattery);
                        if (DEBUG) log("BatteryController.addIconView(circleBattery)");
                    }

                    TextView percText = (TextView) mStatusBarView.findViewWithTag("percentage");
                    if (percText != null) {
                        // add percent text only in case there is no label with "percentage" tag present
                        @SuppressWarnings("unchecked")
                        ArrayList<TextView> mLabelViews = 
                            (ArrayList<TextView>) XposedHelpers.getObjectField(mBatteryController, "mLabelViews");
                        boolean percentTextExists = false;
                        for (TextView tv : mLabelViews) {
                            if ("percentage".equals(tv.getTag())) {
                                percentTextExists = true;
                                break;
                            }
                        }
                        if (!percentTextExists) {
                            XposedHelpers.callMethod(mBatteryController, "addLabelView", percText);
                            if (DEBUG) log("BatteryController.addLabelView(percText)");
                        }
                    }
                }
            });

            XposedBridge.hookAllConstructors(batteryControllerClass, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mBatteryController = param.thisObject;

                    prefs.reload();
                    mBatteryStyle = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_BATTERY_STYLE, "1"));
                    mBatteryPercentText = prefs.getBoolean(GravityBoxSettings.PREF_KEY_BATTERY_PERCENT_TEXT, false);
                    // handle obsolete settings
                    if (mBatteryStyle == 4) {
                        mBatteryStyle = GravityBoxSettings.BATTERY_STYLE_STOCK;
                    }

                    Context context = (Context) XposedHelpers.getObjectField(mBatteryController, "mContext");
                    mMtkPercentText = Utils.isMtkDevice() ?
                            Settings.Secure.getInt(context.getContentResolver(), 
                                    SETTING_MTK_BATTERY_PERCENTAGE, 0) == 1 : false;

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_BATTERY_STYLE_CHANGED);
                    if (Utils.isMtkDevice()) {
                        intentFilter.addAction(ACTION_MTK_BATTERY_PERCENTAGE_SWITCH);
                    }
                    context.registerReceiver(mBroadcastReceiver, intentFilter);

                    if (DEBUG) log("BatteryController constructed");
                }
            });

            findAndHookMethod(batteryControllerClass, "onReceive", Context.class, Intent.class, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    updateBatteryStyle();
                }
            });
        }
        catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void updateBatteryStyle() {
        if (mBatteryController == null) return;

        try {
            @SuppressWarnings("unchecked")
            ArrayList<ImageView> mIconViews = 
                (ArrayList<ImageView>) XposedHelpers.getObjectField(mBatteryController, "mIconViews");
            @SuppressWarnings("unchecked")
            ArrayList<TextView> mLabelViews = 
                (ArrayList<TextView>) XposedHelpers.getObjectField(mBatteryController, "mLabelViews");
    
            for (ImageView iv : mIconViews) {
                if ("stock_battery".equals(iv.getTag())) {
                    iv.setVisibility((mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_STOCK) ?
                                 View.VISIBLE : View.GONE);
                } else if ("circle_battery".equals(iv.getTag())) {
                    iv.setVisibility((mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE ||
                            mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_PERCENT) ?
                            View.VISIBLE : View.GONE);
                    ((CmCircleBattery)iv).setPercentage(
                            mBatteryStyle == GravityBoxSettings.BATTERY_STYLE_CIRCLE_PERCENT);
                }
            }
    
            for (TextView tv : mLabelViews) {
                if ("percentage".equals(tv.getTag())) {
                    tv.setVisibility(
                            ((mBatteryPercentText || mMtkPercentText) ? View.VISIBLE : View.GONE));
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}