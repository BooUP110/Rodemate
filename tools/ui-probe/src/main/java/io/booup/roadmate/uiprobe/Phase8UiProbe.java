package io.booup.roadmate.uiprobe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;

/** Self-targeted TEST-ONLY tool; cannot restart the Production/Demo app. */
public final class Phase8UiProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            SystemClock.sleep(400);
            JSONArray nodes = new JSONArray();
            JSONArray windows = new JSONArray();
            for (AccessibilityWindowInfo window : automation.getWindows()) {
                windows.put(new JSONObject().put("title", String.valueOf(window.getTitle()))
                        .put("type", window.getType()).put("active", window.isActive()));
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String name = String.valueOf(root.getPackageName());
                if (name.startsWith("io.booup.roadmate") || name.contains("inputmethod"))
                    visit(root, nodes, 0);
            }
            result.putString("phase8_ui", new JSONObject().put("nodes", nodes).put("windows", windows)
                    .put("font_scale", getContext().getResources().getConfiguration().fontScale)
                    .put("density_dpi", getContext().getResources().getDisplayMetrics().densityDpi).toString());
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void visit(AccessibilityNodeInfo node, JSONArray rows, int depth) throws Exception {
        if (depth > 60) return;
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        rows.put(new JSONObject().put("text", node.getText() == null ? "" : node.getText().toString())
                .put("content-desc", node.getContentDescription() == null ? "" : node.getContentDescription().toString())
                .put("class", String.valueOf(node.getClassName())).put("package", String.valueOf(node.getPackageName()))
                .put("bounds", "[" + rect.left + "," + rect.top + "][" + rect.right + "," + rect.bottom + "]")
                .put("enabled", String.valueOf(node.isEnabled())).put("focused", String.valueOf(node.isFocused()))
                .put("clickable", String.valueOf(node.isClickable())).put("visible", String.valueOf(node.isVisibleToUser())));
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) visit(child, rows, depth + 1);
        }
    }
}
