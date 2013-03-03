package org.cruk.genesnap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Pattern;

import org.cruk.genesnap.GeneRadarView.GeneRadarThread;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements GeneRadarView.Callback {

  private GeneRadarView mGeneRadarView;
  private GeneRadarThread mGeneRadarThread;
  private TextView mLoadingView;
  private TextView mScoreView;
  private TextView mHighScoreView;
  private ImageView mSplashView;

  private int score = 0;
  private int highScore;
  private float[] mPoints;

  private boolean readyToPlay = false;

  private static final int LOADER_ID_POINTS = 1;

  private static final float POINT_VALUE_TRIM = 0.15f;

  private static final String TAG = MainActivity.class.getName();

  private static final String PREF_HIGH_SCORE = "high_score";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    highScore = prefs.getInt(PREF_HIGH_SCORE, 0);

    mGeneRadarView = (GeneRadarView) findViewById(R.id.radar);
//    mGeneRadarThread = mGeneRadarView.getThread();
    mLoadingView = (TextView) findViewById(R.id.loading_txt);
    mScoreView = (TextView) findViewById(R.id.score_txt);
    mHighScoreView = (TextView) findViewById(R.id.high_score_txt);
    mHighScoreView.setText("" + highScore);
    mSplashView = (ImageView) findViewById(R.id.splash);

    mGeneRadarView.setCallback(this);

    mLoadingView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (readyToPlay)
          start();
      }
    });

    if (savedInstanceState == null) {
      // we were just launched: set up a new game
      // mLunarThread.setState(LunarThread.STATE_READY);
      Log.w(this.getClass().getName(), "SIS is null");
    } else {
      // we are being restored: resume a previous game
      // mLunarThread.restoreState(savedInstanceState);
      Log.w(this.getClass().getName(), "SIS is nonnull");
    }
    getLoaderManager().initLoader(LOADER_ID_POINTS, null,
        new LoaderCallbacks<float[]>() {

          @Override
          public Loader<float[]> onCreateLoader(int id, Bundle args) {
            return new AsyncTaskLoader<float[]>(MainActivity.this) {
              @Override
              protected void onStartLoading() {
                forceLoad();
              }

              @Override
              public float[] loadInBackground() {
                float[] pointArray = null;
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getContext().getResources()
                        .openRawResource(R.raw.chrom1)));
                try {
                  LinkedList<Float> points = new LinkedList<Float>();
                  String line = reader.readLine();
                  float firstPos = -1;
                  Pattern splitter = Pattern.compile("\\s+");
                  while (line != null) {
                    // //Log.d(TAG, "Trying to match on: " + line);
                    String[] groups = splitter.split(line);
                    float pos = Float.parseFloat(groups[1]);
                    float value = Float.parseFloat(groups[2]);
                    if (value >= POINT_VALUE_TRIM
                        && value <= 1 - POINT_VALUE_TRIM) {
                      value = (value - 0.15f) / 0.7f;
                      if (firstPos == -1) {
                        firstPos = pos;
                      }
                      pos -= firstPos;
                      // //Log.d(TAG, "Adding point for pos " + pos + " value "
                      // +
                      // value);
                      points.add(pos);
                      points.add(value);
                    }
                    line = reader.readLine();
                  }
                  pointArray = new float[points.size()];
                  Iterator<Float> iter = points.iterator();
                  for (int i = 0; i < points.size(); i++) {
                    pointArray[i] = iter.next();
                  }
                } catch (IOException e) {
                  Log.e(TAG, "Exception reading data file", e);
                } finally {
                  try {
                    reader.close();
                  } catch (IOException e) {
                    Log.e(TAG, "Exception closing the reader", e);
                  }
                }
                return pointArray;
              }
            };
          }

          @Override
          public void onLoadFinished(Loader<float[]> arg0, float[] arg1) {
            mPoints = arg1;
            start();
          }

          @Override
          public void onLoaderReset(Loader<float[]> arg0) {
            // TODO Auto-generated method stub
          }

        });

  }

  private void start() {
    mLoadingView.setVisibility(View.GONE);
    mSplashView.setVisibility(View.GONE);
    mGeneRadarThread = mGeneRadarView.newThread();
    mGeneRadarThread.setPoints(mPoints);
    mGeneRadarThread.doStart();
    mGeneRadarView.setVisibility(View.VISIBLE);

    readyToPlay = false;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public void updateScore(int newScore) {
    score = newScore;
    mScoreView.setText("" + newScore);
  }

  @Override
  public void endGame() {
    String text = "You finished! Well Done!!\n(click to try again)";
    if (score >= highScore) {
      highScore = score;
      text = "HIGH SCORE!!!!!!111one\n(click to try again)";
      mHighScoreView.setText("" + highScore);
      SharedPreferences.Editor prefsEditor = getPreferences(MODE_PRIVATE)
          .edit();
      prefsEditor.putInt(PREF_HIGH_SCORE, highScore);
      prefsEditor.commit();
    }
    mGeneRadarView.setVisibility(View.GONE);
    mLoadingView.setVisibility(View.VISIBLE);
    mLoadingView.setText(text);

    readyToPlay = true;
  }
}
