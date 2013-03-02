package org.cruk.genesnap;

import org.cruk.genesnap.GeneRadarView.GeneRadarThread;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

public class MainActivity extends Activity {

  private GeneRadarView mGeneRadarView;
  private GeneRadarThread mGeneRadarThread;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mGeneRadarView = (GeneRadarView) findViewById(R.id.radar);
    mGeneRadarThread = mGeneRadarView.getThread();

    if (savedInstanceState == null) {
      // we were just launched: set up a new game
//      mLunarThread.setState(LunarThread.STATE_READY);
      Log.w(this.getClass().getName(), "SIS is null");
    } else {
      // we are being restored: resume a previous game
//      mLunarThread.restoreState(savedInstanceState);
      Log.w(this.getClass().getName(), "SIS is nonnull");
    }
    mGeneRadarThread.doStart();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

}
