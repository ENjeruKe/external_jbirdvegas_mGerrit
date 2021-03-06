package com.jbirdvegas.mgerrit.helpers;

import android.app.Activity;
import android.content.Context;

public interface AnalyticsSender {
    void sendAnalyticsEvent(Context context, String category, String action, String label, Long value);

    void startActivity(Activity activity);

    void stopActivity(Activity activity);

    void initAnalytics(Context context);

    void setCustomString(String key, String data);

    void setCustomInt(String key, int data);

    void logException(Exception e);
}
