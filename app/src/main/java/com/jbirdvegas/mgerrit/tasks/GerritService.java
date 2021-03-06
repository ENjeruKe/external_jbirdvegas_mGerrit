package com.jbirdvegas.mgerrit.tasks;

/*
 * Copyright (C) 2013 Android Open Kang Project (AOKP)
 *  Author: Evan Conway (P4R4N01D), 2013
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

public class GerritService extends IntentService {

    public static final String TAG = "GerritService";

    public static final String DATA_TYPE_KEY = "Type";

    /* These are for the change list - whether we are fetching newer or older changes than what
      * we have already */
    public enum Direction { Newer, Older }
    public static final String CHANGES_LIST_DIRECTION = "direction";
    public static final String CHANGE_STATUS = "status";
    public static final String CHANGE_KEYWORDS = "keywords";

    // AccountProcessor
    public static final String HTTP_USERNAME = "username";
    public static final String HTTP_PASSWORD = "password";

    // StarProcessor
    public static final String IS_STARRING = "is_starred";
    public static final String CHANGE_ID = "change_id";
    public static final String CHANGE_NUMBER = "change_no";

    // DiffProcessor & ImageProcessor
    public static final String PATCHSET_NUMBER = "ps_no";
    public static final String FILE_PATH = "file_path";
    public static final String FILE_STATUS = "file_status";

    // Bypass the sync required logic and force a sync
    public static final String FORCE_UPDATE = "force";

    // ReviewProcessor
    public static final String REVIEW_MESSAGE = "message";
    public static final String CHANGE_LABELS = "labels";

    public enum DataType { Project, Commit, CommitDetails, GetVersion, Account, Star, Diff, Image, Comment }

    // A list of the currently running sync processors
    //  We should use some token system where a given query is assigned a token which we can use
    //  to cancel it
    private static SparseArray<SyncProcessor> sRunningTasks;
    private static int sQueueId = 0;

    // This is required for the service to be started
    public GerritService() {
        super(TAG);
        sRunningTasks = new SparseArray<>();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        SyncProcessor processor;

        // We cannot do anything if no intent was provided
        if (intent == null) return;

        // Determine which SyncProcessor to use here
        DataType dataType = (DataType) intent.getSerializableExtra(DATA_TYPE_KEY);
        if (dataType == DataType.Project) {
            processor = new ProjectListProcessor(this, intent);
        } else if (dataType == DataType.Commit) {
            processor = new ChangeListProcessor(this, intent);
        } else if (dataType == DataType.CommitDetails) {
            processor = new CommitProcessor(this, intent);
        } else if (dataType == DataType.GetVersion) {
            processor = new VersionProcessor(this, intent);
        } else if (dataType == DataType.Account) {
            processor = new AccountProcessor(this, intent);
        } else if (dataType == DataType.Star) {
            processor = new StarProcessor(this, intent);
        } else if (dataType == DataType.Diff) {
            processor = new TextDiffProcessor(this, intent);
        } else if (dataType == DataType.Image) {
                processor = new ImageProcessor(this, intent);
        } else if (dataType == DataType.Comment) {
            processor = new ReviewProcessor(this, intent);
        } else {
            Log.w(TAG, "Don't know how to handle synchronization of type " + DATA_TYPE_KEY);
            return;
        }

        boolean force = intent.getBooleanExtra(FORCE_UPDATE, false);

        // We may already be running this type of sync processor so check if it is allowed
        //  to be added to the list of running sync processors
        if (allowNewInstance(processor)) {
            // Call the SyncProcessor to fetch the data if necessary
            boolean needsSync = processor.isSyncRequired(this);
            if (needsSync || force) {
                int queueId = sQueueId;
                sQueueId += 1;
                sRunningTasks.put(queueId, processor);
                processor.setQueueId(queueId);
                processor.fetchData();
            }
        }
    }

    /**
     * Start the updater to check for an update if necessary
     */
    public static void sendRequest(Context context, DataType dataType, Bundle bundle) {
        Intent it = new Intent(context, GerritService.class);
        it.putExtra(GerritService.DATA_TYPE_KEY, dataType);
        it.putExtras(bundle);
        context.startService(it);
    }

    protected boolean allowNewInstance(SyncProcessor processor) {
        for (int i = 0; i < sRunningTasks.size(); i++) {
            SyncProcessor next = sRunningTasks.valueAt(i);
            if (processor.doesProcessorConflict(next)) return false;
        }
        return true;
    }

    protected static void finishedRequest(int queueId) {
        sRunningTasks.remove(queueId);
    }
}
