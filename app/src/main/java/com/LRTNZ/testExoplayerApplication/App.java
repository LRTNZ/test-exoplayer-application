package com.LRTNZ.testExoplayerApplication;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

/**
 * Main Activity of the application
 */
public class App extends Activity implements Runnable {


    /**
     * {@link Integer} value of which of the two streams is being played
     */
    // Actual first stream to be played is the inverse of this, just the quick logic setup I put together needs it this way
    int currentStreamIndex = 1;


    /**
     * {@link String} value of the network address of the current streaming source to be played back
     */
    String currentStreamAddress = "";

    // Text box at the top of the screen, that will have the current stream name/index being played in it, to make it easy to see what is happening in the application

    /**
     * {@link EditText} that is the box at the top of the screen showing the details about the current stream being played
     */
    EditText streamName;


    /**
     * {@link ArrayList}<{@link String}> of the two IP addresses of the multicast streams that are to be cycled through.
     * These are where you load in the addressed of the two multicast streams you are creating on your own network, to run this application.
     */

    // |---------------------------|
    // | Configure stream IPs here |
    // |---------------------------|

    ArrayList<String> streamAddresses = new ArrayList<String>() {{
        add("udp://@239.7.0.3:1234");
        add("udp://@239.7.0.7:1234");
    }};


    static int numPlaybacks = 0;

    private DataSource.Factory datasourceFactory;
    private volatile MediaSource mediaSource;
    private PlayerView playerView;
    private SimpleExoPlayer exoPlayer;
    private DefaultTrackSelector defaultTrackSelector;
    private long playbackPosition = 0;
    private TextView subtitlesView;


    @Override
    protected void onCreate(Bundle savedInstance) {

        // Run the super stuff for this method
        super.onCreate(savedInstance);

        Timber.plant(new Timber.DebugTree() {
            @Override
            protected void log(int priority, String tag, @NotNull String message, Throwable t) {
                super.log(priority, "Test-Exoplayer", message, t);
            }
        });
        Timber.d("In debug mode");

        // Sets the main view
        setContentView(R.layout.main);

        // Load the editText variable with a reference to what it needs to fill in the layout
        streamName = findViewById(R.id.stream_ID);

        // Run the exoplayer creation/init method
        createExoplayer();
    }


    private DataSource.Factory buildDataSourceFactory() {
        return new DefaultDataSourceFactory(this, Util.getUserAgent(this, "Test"));
    }

    public void createExoplayer() {

        DefaultLoadControl defaultLoadControl = new DefaultLoadControl.Builder()
                                                    .setBufferDurationsMs(1000, 2500, 1000, 1000).createDefaultLoadControl();
        defaultTrackSelector = new DefaultTrackSelector(this);

        Timber.d("Track Selector Parameters: %s", defaultTrackSelector.getParameters().toString());

        exoPlayer = new SimpleExoPlayer.Builder(this).setLoadControl(defaultLoadControl)
                                                     .setTrackSelector(defaultTrackSelector).build();
        int currentWindows = 0;
        exoPlayer.seekTo(currentWindows, playbackPosition);

        exoPlayer.addListener(new eventListener());

        this.datasourceFactory = buildDataSourceFactory();
        this.playerView = findViewById(R.id.video_surface);
        playerView.setPlayer(exoPlayer);
        subtitlesView = findViewById(R.id.subtitle);

        TextOutput listener = cues -> {
            if (cues.size() >= 2) {
                subtitlesView.setText(cues.get(1).text);
            }
        };
        exoPlayer.addTextOutput(listener);
        playerView.requestFocus();

        // Call the change stream, to preload the first stream at startup, instead of waiting for an input
        changeStream();

        // If you do not have the means to automatically generate an alternative two pulse up/two pulse down signal input for the Android TV,
        // these two lines can be uncommented in order to enable the automatic up/down changing.
        // The reason there are the two input options, is to prove it is not the source of the call to changing the stream that is causing the issues with the crashing.

        // |------------------------------------|
        // | Optional automatic stream changing |
        // |------------------------------------|

        runAutomaticTimer = true;
        runTimedStreamChange();
    }

    private static class eventListener implements Player.EventListener {

        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

            String playbackStateString;

            switch (playbackState) {

                case ExoPlayer.STATE_IDLE:
                    playbackStateString = "Player Idle";
                    break;
                case ExoPlayer.STATE_BUFFERING:
                    playbackStateString = "Player Buffering";
                    break;
                case ExoPlayer.STATE_READY:
                    playbackStateString = "Player Ready";
                    break;
                case ExoPlayer.STATE_ENDED:
                    playbackStateString = "Player Ended";
                    break;
                default:
                    playbackStateString = "Player in unknown state";
                    Timber.wtf("Player somehow has an unknown state!?");
                    break;
            }

            Timber.d("Playback state: %s", playbackStateString);
            // Timber.d("called this");
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            IOException e = error.getSourceException();
            Timber.e(error, "Error direct from exoplayer is: ");
            Timber.e(e, "Error underlaying from exoplayer: ");
            Timber.d("Error underlaying class name: %s", e.getClass().getName());
            Timber.d("Error underlaying error name: %s", e.getCause().getClass().getName());
            if (e.getCause() instanceof SocketTimeoutException) {
                Timber.d("This is an error that can be caught!");
                Timber.e(e, "Network connection timed out from stream");
                Timber.d("Not retrying as not in full app");
            }

        }
    }




    @Override
    public void onNewIntent(Intent intent) {

        // Standard android stuff
        super.onNewIntent(intent);
        Timber.d("Player ran new intent");

        setIntent(intent);
    }

    @Override
    public void onStart() {

        // Run super stuff
        super.onStart();

    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("App ran resume");
    }

    @Override
    public void onPause() {
        super.onPause();
        Timber.d("App ran paused");
    }

    @Override
    public void onStop() {
        super.onStop();

        Timber.d("Player ran stop");
    }


    /**
     * {@link Boolean} value that stores whether or not the automatic timer should cancel, once it has been set going
     */
    volatile boolean runAutomaticTimer = false;

    /**
     * Method that can be called to start a timer to automatically change the stream every 10 seconds from inside the application.
     */
    void runTimedStreamChange() {

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!runAutomaticTimer) {
                    this.cancel();
                }
                runOnUiThread(() -> changeStream());
            }
        }, 5000, 1000 * 30);
    }


    /**
     * Method that is called to change the multicast stream Exoplayer is currently playing
     */
    void changeStream() {

        // If the current stream being played is the first
        if (currentStreamIndex == 0) {

            currentStreamIndex = 1;

            Timber.d("Selected Stream: 1");
            currentStreamAddress = streamAddresses.get(1);

            // Perform the inverse if the second stream is currently playing
        } else {

            currentStreamIndex = 0;

            Timber.d("Selected Stream: 0");
            currentStreamAddress = streamAddresses.get(0);

        }

        // Load the values of the current stream and index into the textbox at the top of the screen, to make it easier to see what is happening
        streamName.setText(String.format("Stream: %s/%s", currentStreamIndex, currentStreamAddress));

        // TODO do processing of stuff for new media here

        MediaSource videoSource;

        videoSource = new ProgressiveMediaSource.Factory(this.datasourceFactory).createMediaSource(Uri.parse(currentStreamAddress));
        Format subtitleFormat = Format.createTextSampleFormat(null, "text/x-ssa", C.SELECTION_FLAG_DEFAULT, "en");
        MediaSource subtitleMediaSource = new SingleSampleMediaSource.Factory(this.datasourceFactory).createMediaSource(Uri.parse("https://drive.google.com/u/1/uc?id=1nGMnErfRGXb8pEG3iFP_AZuMpmwLhpC5&export=download"), subtitleFormat, C.TIME_UNSET);
        videoSource = new MergingMediaSource(videoSource, subtitleMediaSource);
        this.defaultTrackSelector.setParameters(this.defaultTrackSelector.buildUponParameters().setPreferredTextLanguage("en"));

        this.mediaSource = videoSource;

        // Finish up the process of loading the stream into the player
        finishPlayer();
    }

    /**
     * Method that is called to load in a new mediasource and to set it playing out the output, from Exoplayer
     */
    void finishPlayer() {

        subtitlesView.setVisibility(View.VISIBLE);
        exoPlayer.prepare(mediaSource, true, true);
        exoPlayer.setVolume(1f);
        playerView.setAlpha(1f);

        exoPlayer.setPlayWhenReady(true);

         //If subtitle clearer needs to be running
        if (!subtitleClearingRunnableRunning) {
            Timber.d("Subtitles: Subtitle runnable not already running");
            this.run_subtitle_clear = true;
            this.run();
        }

        Timber.d("Number of playbacks: %s", numPlaybacks);
        numPlaybacks++;
    }


    private volatile boolean run_subtitle_clear = false;
    private static volatile boolean subtitleClearingRunnableRunning = false;
    private String oldSubtitle = "";
    private final Handler h2 = new Handler();

    @Override
    public void run() {

        if (!subtitleClearingRunnableRunning) {
            subtitleClearingRunnableRunning = true;
        }

        // For Ads Sceen
        String subtitle = subtitlesView.getText().toString();
        if (subtitle.length() != 0) {
            if (subtitle.equals(oldSubtitle)) {
                subtitlesView.setText("");
                //Timber.d("Subtitles: Clearing old subtitles");
            }
            oldSubtitle = subtitle;
        }

        if (run_subtitle_clear) {
            // Timber.d("Subtitles: Clear subtitles handler looping");
            h2.postDelayed(this, 5000);
        } else {
            //Timber.d("Subtitles: Clear subtitles handler ending");
            subtitleClearingRunnableRunning = false;
        }
    }


    /**
     * {@link Boolean} value that stores whether button inputs are to be observed or not at the current time by the app.
     */
    volatile boolean buttonLockout = false;

    /**
     * Enum that represents the direction the channel change button on the remote was pressed
     */
    private enum directionPressedEnum {
        STREAM_UP,
        STREAM_DOWN
    }

    boolean pressedOnce = false;
    boolean secondPress = false;
    /**
     * Stores the curremt {@link directionPressedEnum} of what button direction was last pushed
     */
    static directionPressedEnum directionPressed = null;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Debug: Log that a button that can be read in by the program has been pressed
        Timber.v("Remote button was pressed");

        // If the app is to observe the button presses or not. Required due to the fact the TV this is being tested on (Sony Bravia) likes to sometimes read in extraneous button presses that are non existent.
        if (!buttonLockout) {

            // If the button pressed was the channel up
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {

                // Debug
                Timber.d("Channel up pressed");

                // If the direction that was last pressed was down/app is starting
                if (directionPressed == directionPressedEnum.STREAM_DOWN || directionPressed == null) {

                    Timber.d("First up press");

                    // Set the direction that has been pressed, and that the channel button has been pressed once
                    directionPressed = directionPressedEnum.STREAM_UP;
                    pressedOnce = true;

                    // Otherwise if the last press was already in this direction
                } else if (directionPressed == directionPressedEnum.STREAM_UP && pressedOnce) {
                    Timber.d("Second up press");

                    // Button being pressed for a second time, so lock out taking in any more inputs
                    secondPress = true;
                    buttonLockout = true;
                }

                // Same as above, just for the channel down key on the remote
            } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {

                Timber.d("Channel down pressed");
                if (directionPressed == directionPressedEnum.STREAM_UP || directionPressed == null) {

                    Timber.d("First down press");
                    directionPressed = directionPressedEnum.STREAM_DOWN;
                    pressedOnce = true;
                } else if (directionPressed == directionPressedEnum.STREAM_DOWN && pressedOnce) {
                    Timber.d("Second down press");
                    secondPress = true;
                    buttonLockout = true;
                }

                // Catches other button presses that the program has received
            } else {
                Timber.d("Other button press");
                //return super.onKeyDown(keyCode, event);
            }

            // If the button has been pressed for a second time, and the button input has been locked out
            if (secondPress && buttonLockout) {
                Timber.d("Change stream called");

                // Reset variables
                pressedOnce = false;
                secondPress = false;

                // Call the stream change
                changeStream();

                // Call the handler to reset the lockout after a timeout
                handleButtonLockout();
            }

            // Return true to stop the OS pulling you out of this app on any button press
            return true;
        }

        // return true so any buttons read in that the program doesn't handle, doesn't close the program
        return true;
    }

    /**
     * Handler to reset the {@link #buttonLockout} value after a timeout period
     */
    void handleButtonLockout() {

        // Create new Handler
        Handler handler = new Handler();
        // Run this runnable after a second
        handler.postDelayed(() -> buttonLockout = false, 1000);

    }

}