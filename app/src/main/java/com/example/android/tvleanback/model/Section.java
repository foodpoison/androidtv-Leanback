package com.example.android.tvleanback.model;

import java.util.List;

public class Section {

    protected String id;
    protected String title;

    protected List<Collection> collections;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Collection> getCollections() {
        return collections;
    }
}
