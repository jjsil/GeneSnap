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
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements GeneRadarView.Callback {

  private GeneRadarView mGeneRadarView;
  private GeneRadarThread mGeneRadarThread;
  private TextView mLoadingView;
  private TextView mScoreView;

  private static final int LOADER_ID_POINTS = 1;

  private static final String TAG = MainActivity.class.getName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mGeneRadarView = (GeneRadarView) findViewById(R.id.radar);
    mGeneRadarThread = mGeneRadarView.getThread();
    mLoadingView = (TextView) findViewById(R.id.loading_txt);
    mScoreView = (TextView) findViewById(R.id.score_txt);

    mGeneRadarView.setCallback(this);

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
                    if (firstPos == -1) {
                      firstPos = pos;
                    }
                    pos -= firstPos;
                    // //Log.d(TAG, "Adding point for pos " + pos + " value " +
                    // value);
                    points.add(pos);
                    points.add(value);
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
            mLoadingView.setVisibility(View.GONE);
            mGeneRadarView.setVisibility(View.VISIBLE);
            mGeneRadarThread.setPoints(arg1);
            mGeneRadarThread.doStart();
          }

          @Override
          public void onLoaderReset(Loader<float[]> arg0) {
            // TODO Auto-generated method stub
          }

        });

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public void updateScore(int newScore) {
    mScoreView.setText("" + newScore);
  }

  @Override
  public void endGame() {
    mGeneRadarView.setVisibility(View.GONE);
    mLoadingView.setVisibility(View.VISIBLE);
    mLoadingView.setText("You finished! Well Done!!" +
    		"!!\nYou had 90% overlap with your friend Joe");
  }
}
