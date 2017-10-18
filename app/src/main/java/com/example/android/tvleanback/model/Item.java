package com.example.android.tvleanback.model;

import java.util.Map;

public class Item {
    protected Map<String, String> title;
    protected String contentKey;
    protected String contentType;
    protected String imagePackId;
    protected String parentalGuidance;

    public String getTitle() {
        if (title != null && !title.isEmpty()) {
            return (String)title.values().toArray()[0];
        }
        return "";
    }

    public String getContentKey() {
        return contentKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getImagePackId() {
        return imagePackId;
    }

    public String getParentalGuidance() {
        return parentalGuidance;
    }
}
