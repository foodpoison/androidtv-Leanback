/*
 * Copyright (c) 2014 The Android Open Source Project
 *
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

package com.example.android.tvleanback.ui;

import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.example.android.tvleanback.R;
import com.example.android.tvleanback.Utils;
import com.example.android.tvleanback.data.FetchVideoService;
import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.model.Collection;
import com.example.android.tvleanback.model.MetaData;
import com.example.android.tvleanback.model.Section;
import com.example.android.tvleanback.model.Video;
import com.example.android.tvleanback.model.VideoCursorMapper;
import com.example.android.tvleanback.presenter.CardPresenter;
import com.example.android.tvleanback.presenter.GridItemPresenter;
import com.example.android.tvleanback.presenter.IconHeaderItemPresenter;
import com.example.android.tvleanback.recommendation.UpdateRecommendationsService;
import com.google.gson.Gson;

import java.util.Timer;
import java.util.TimerTask;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseFragment {
    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private Uri mBackgroundURI;
    private BackgroundManager mBackgroundManager;
    private static final int CATEGORY_LOADER = 123; // Unique ID for Category Loader.

    private MetaData metaData;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager();

        setupUIElements();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        updateRecommendations();

        // load custom data
        String jsonText = Utils.loadTextFileContentsFromAsset(this.getActivity(), "input.json");
        metaData = new Gson().fromJson(jsonText, MetaData.class);
        onLoadFinished(metaData);

    }

    @Override
    public void onDestroy() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
            mBackgroundTimer = null;
        }
        mBackgroundManager = null;
        super.onDestroy();
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = getResources().getDrawable(R.drawable.default_background, null);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        setBadgeDrawable(
                getActivity().getResources().getDrawable(R.drawable.videos_by_google_banner, null));
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter();
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        Glide.with(this)
                .load(uri)
                .asBitmap()
                .centerCrop()
                .error(mDefaultBackground)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap>
                            glideAnimation) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private void updateRecommendations() {
        Intent recommendationIntent = new Intent(getActivity(), UpdateRecommendationsService.class);
        getActivity().startService(recommendationIntent);
    }

    private void onLoadFinished(MetaData metaData) {
        mCategoryRowAdapter.clear();

        for (Section section : metaData.getSections()) {
            for (Collection col : section.getCollections()) {

                ArrayObjectAdapter itemsAdapter = new ArrayObjectAdapter(new CardPresenter());
                itemsAdapter.addAll(0, col.getItems());

                ListRow row = new ListRow(new HeaderItem(col.getTitle()), itemsAdapter);
                mCategoryRowAdapter.add(row);
            }
        }
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundURI != null) {
                        updateBackground(mBackgroundURI.toString());
                    }
                }
            });
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof String) {
                if (((String) item).contains(getString(R.string.grid_view))) {
                    Intent intent = new Intent(getActivity(), VerticalGridActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else if (((String) item).contains(getString(R.string.guidedstep_first_title))) {
                    Intent intent = new Intent(getActivity(), GuidedStepActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else if (((String) item).contains(getString(R.string.error_fragment))) {
                    BrowseErrorFragment errorFragment = new BrowseErrorFragment();
                    getFragmentManager().beginTransaction().replace(R.id.main_frame, errorFragment)
                            .addToBackStack(null).commit();
                } else if(((String) item).contains(getString(R.string.personal_settings))) {
                    Intent intent = new Intent(getActivity(), SettingsActivity.class);
                    Bundle bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                                    .toBundle();
                    startActivity(intent, bundle);
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                mBackgroundURI = Uri.parse(((Video) item).bgImageUrl);
                startBackgroundTimer();
            }

        }
    }
}
