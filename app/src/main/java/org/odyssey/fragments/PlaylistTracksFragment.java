package org.odyssey.fragments;

import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.odyssey.OdysseyMainActivity;
import org.odyssey.R;
import org.odyssey.adapter.TracksListViewAdapter;
import org.odyssey.loaders.TrackLoader;
import org.odyssey.models.TrackModel;
import org.odyssey.playbackservice.PlaybackServiceConnection;
import org.odyssey.utils.MusicLibraryHelper;
import org.odyssey.utils.PermissionHelper;
import org.odyssey.utils.ThemeUtils;

import java.util.List;

public class PlaylistTracksFragment extends OdysseyFragment implements LoaderManager.LoaderCallbacks<List<TrackModel>>, AdapterView.OnItemClickListener {

    /**
     * Adapter used for the ListView
     */
    private TracksListViewAdapter mTracksListViewAdapter;

    /**
     * Save the swipe layout for later usage
     */
    private SwipeRefreshLayout mSwipeRefreshLayout;

    /**
     * ServiceConnection object to communicate with the PlaybackService
     */
    private PlaybackServiceConnection mServiceConnection;

    /**
     * Key values for arguments of the fragment
     */
    // FIXME move to separate class to get unified constants?
    public final static String ARG_PLAYLISTTITLE = "playlisttitle";
    public final static String ARG_PLAYLISTID = "playlistid";

    /**
     * The information of the displayed playlist
     */
    private String mPlaylistTitle = "";
    private long mPlaylistID = -1;

    /**
     * Called to create instantiate the UI of the fragment.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.list_refresh, container, false);

        // get listview
        ListView playlistTracksListView = (ListView) rootView.findViewById(R.id.list_refresh_listview);

        // get swipe layout
        mSwipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.list_refresh_swipe_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mTracksListViewAdapter = new TracksListViewAdapter(getActivity());

        playlistTracksListView.setAdapter(mTracksListViewAdapter);

        playlistTracksListView.setOnItemClickListener(this);

        registerForContextMenu(playlistTracksListView);

        Bundle args = getArguments();

        mPlaylistTitle = args.getString(ARG_PLAYLISTTITLE);
        mPlaylistID = args.getLong(ARG_PLAYLISTID);

        return rootView;
    }

    /**
     * Called when the fragment resumes.
     * Reload the data, setup the toolbar and create the PBS connection.
     */
    @Override
    public void onResume() {
        super.onResume();

        // set toolbar behaviour and title
        OdysseyMainActivity activity = (OdysseyMainActivity) getActivity();
        activity.setUpToolbar(mPlaylistTitle, false, false, false);

        // set up play button
        activity.setUpPlayButton(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPlaylist(0);
            }
        });

        // set up pbs connection
        mServiceConnection = new PlaybackServiceConnection(getActivity().getApplicationContext());
        mServiceConnection.openConnection();

        // change refresh state
        mSwipeRefreshLayout.setRefreshing(true);
        // Prepare loader ( start new one or reuse old )
        getLoaderManager().initLoader(0, getArguments(), this);
    }

    /**
     * This method creates a new loader for this fragment.
     *
     * @param id     The id of the loader
     * @param bundle Optional arguments
     * @return Return a new Loader instance that is ready to start loading.
     */
    @Override
    public Loader<List<TrackModel>> onCreateLoader(int id, Bundle bundle) {
        return new TrackLoader(getActivity(), "", mPlaylistID);
    }

    /**
     * Called when the loader finished loading its data.
     *
     * @param loader The used loader itself
     * @param data   Data of the loader
     */
    @Override
    public void onLoadFinished(Loader<List<TrackModel>> loader, List<TrackModel> data) {
        mTracksListViewAdapter.swapModel(data);

        // change refresh state
        mSwipeRefreshLayout.setRefreshing(false);
    }

    /**
     * If a loader is reset the model data should be cleared.
     *
     * @param loader Loader that was resetted.
     */
    @Override
    public void onLoaderReset(Loader<List<TrackModel>> loader) {
        mTracksListViewAdapter.swapModel(null);
    }

    /**
     * generic method to reload the dataset displayed by the fragment
     */
    @Override
    public void refresh() {
        // reload data
        getLoaderManager().restartLoader(0, getArguments(), this);
    }

    /**
     * Play the playlist from the current position.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        playPlaylist(position);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_playlist_tracks_fragment, menu);
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if (info == null) {
            return super.onContextItemSelected(item);
        }

        switch (item.getItemId()) {
            case R.id.fragment_playlist_tracks_action_enqueue:
                enqueueTrack(info.position);
                return true;
            case R.id.fragment_playlist_tracks_action_enqueueasnext:
                enqueueTrackAsNext(info.position);
                return true;
            case R.id.fragment_playlist_tracks_action_remove:
                removeTrackFromPlaylist(info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Call the PBS to play the entire playlist and start with the selected track.
     * A previous playlist will be cleared.
     *
     * @param position the position of the selected track in the adapter
     */
    private void playPlaylist(int position) {

        try {
            // clear the current playlist
            mServiceConnection.getPBS().clearPlaylist();

            // add the playlist
            mServiceConnection.getPBS().enqueuePlaylist(mPlaylistID);

            // jump to selected track
            mServiceConnection.getPBS().jumpTo(position);
        } catch (RemoteException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    /**
     * Call the PBS to enqueue the selected track.
     *
     * @param position the position of the selected track in the adapter
     */
    private void enqueueTrack(int position) {
        // Enqueue single track

        try {
            TrackModel track = (TrackModel) mTracksListViewAdapter.getItem(position);
            mServiceConnection.getPBS().enqueueTrack(track);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Call the PBS to enqueue the selected track as the next track.
     *
     * @param position the position of the selected track in the adapter
     */
    private void enqueueTrackAsNext(int position) {
        // Enqueue single track as next

        try {
            TrackModel track = (TrackModel) mTracksListViewAdapter.getItem(position);
            mServiceConnection.getPBS().enqueueTrackAsNext(track);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Remove the selected track from the playlist in the mediastore.
     *
     * @param position the position of the selected track in the adapter
     */
    private void removeTrackFromPlaylist(int position) {
        Cursor trackCursor = PermissionHelper.query(getActivity(), MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistID), MusicLibraryHelper.projectionPlaylistTracks, "", null, "");

        if (trackCursor != null) {
            if (trackCursor.moveToPosition(position)) {
                String where = MediaStore.Audio.Playlists.Members._ID + "=?";
                String[] whereVal = {trackCursor.getString(trackCursor.getColumnIndex(MediaStore.Audio.Playlists.Members._ID))};

                PermissionHelper.delete(getActivity(), MediaStore.Audio.Playlists.Members.getContentUri("external", mPlaylistID), where, whereVal);

                // reload data
                getLoaderManager().restartLoader(0, getArguments(), this);
            }

            trackCursor.close();
        }
    }
}
