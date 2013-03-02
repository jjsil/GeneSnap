package org.cruk.genesnap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Deque;
import java.util.LinkedList;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GeneRadarView extends SurfaceView implements
    SurfaceHolder.Callback {

  private static final String TAG = GeneRadarView.class.getName();

  static class GeneRadarThread extends Thread {

    private static final int START_DEGREES = 45;
    private static final int SWEEP_DEGREES = 90;

    private static final int RADAR_MARGIN = -50;
    private static final int RADAR_INNER_RADIUS = 100;

    private static final int TIME_MULTIPLIER = 4;

    private static final float POS_TO_DEGREE_CONVERSION_FACTOR = 0.000002f;

    class PolarPoint {
      private float radius;
      private float degrees;
      private double radians;

      public PolarPoint(float radius, float degrees) {
        this.radius = radius;
        this.degrees = degrees;
        radians = Math.PI * this.degrees / 180;
      }

      public void increaseAngle(float degrees) {
        this.degrees += degrees;
        radians = (Math.PI * this.degrees) / 180;
      }

      public float getAngleDegrees() {
        return degrees;
      }

      private float getX(int width) {
        return (float) (Math.cos(radians) * (RADAR_INNER_RADIUS + radius
            * width / 2));
      }

      private float getY(int width) {
        return (float) (Math.sin(radians) * (RADAR_INNER_RADIUS + radius
            * width / 2));
      }

      public void draw(float centreX, float centreY, int width, Canvas canvas) {
        float x = getX(width);
        float y = getY(width);
        // Log.d(TAG, "Drawing point at " + x + ", " + y);
        canvas.drawCircle(centreX + x, centreY - y, 2, mPointPaint);
      }
    }

    /**
     * Current height of the surface/canvas.
     *
     * @see #setSurfaceSize
     */
    private int mCanvasHeight = 1;

    /**
     * Current width of the surface/canvas.
     *
     * @see #setSurfaceSize
     */
    private int mCanvasWidth = 1;

    /**
     * Current diameter of the radar.
     *
     * @see #setSurfaceSize
     */
    private int mRadarDiameter = 1;

    /** Used to figure out elapsed time between frames */
    private long mLastTime;

    /** Used to figure out the total elapsed time since the beginning */
    private long mStartTime;

    /** Message handler used by thread to interact with TextView */
    private Handler mHandler;

    /** Indicate whether the surface has been created & is ready to draw */
    private boolean mRun = false;

    /** Handle to the surface manager object we interact with */
    private SurfaceHolder mSurfaceHolder;

    /** Handle to the application context, used to e.g. fetch Drawables. */
    private Context mContext;

    private RectF mScratchRect;

    /** Paint to draw the lines on screen. */
    private Paint mLinePaint;

    /** Paint to draw individual points in the plot */
    private Paint mPointPaint;

    private Paint mBackgroundPaint;

    private Deque<PolarPoint> mPoints;

    public GeneRadarThread(SurfaceHolder surfaceHolder, Context context,
        Handler handler) {
      // get handles to some important objects
      mSurfaceHolder = surfaceHolder;
      mHandler = handler;
      mContext = context;

      mPoints = new LinkedList<PolarPoint>();

      Resources res = context.getResources();
      // cache handles to our key sprites & other drawables
      // mLanderImage = context.getResources()
      // .getDrawable(R.drawable.lander_plain);
      // mFiringImage = context.getResources().getDrawable(
      // R.drawable.lander_firing);
      // mCrashedImage = context.getResources().getDrawable(
      // R.drawable.lander_crashed);

      // load background image as a Bitmap instead of a Drawable b/c
      // we don't need to transform it and it's faster to draw this way
      // mBackgroundImage = BitmapFactory
      // .decodeResource(res, R.drawable.earthrise);

      // Use the regular lander image as the model size for all sprites
      // mLanderWidth = mLanderImage.getIntrinsicWidth();
      // mLanderHeight = mLanderImage.getIntrinsicHeight();

      // Initialize paints for speedometer
      mLinePaint = new Paint();
      mLinePaint.setAntiAlias(true);
      mLinePaint.setARGB(255, 0, 255, 0);
      //
      // mLinePaintBad = new Paint();
      // mLinePaintBad.setAntiAlias(true);
      // mLinePaintBad.setARGB(255, 120, 180, 0);
      //
      mPointPaint = new Paint();
      mPointPaint.setAntiAlias(true);
      mPointPaint.setColor(res.getColor(android.R.color.holo_blue_light));

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setAntiAlias(true);
      mBackgroundPaint.setColor(res.getColor(android.R.color.background_light));

      mScratchRect = new RectF(0, 0, 0, 0);

      // initial show-up of lander (not yet playing)
      // mX = mLanderWidth;
      // mY = mLanderHeight * 2;
      // mFuel = PHYS_FUEL_INIT;
      // mDX = 0;
      // mDY = 0;
      // mHeading = 0;
      // mEngineFiring = true;
    }

    @Override
    public void run() {
      while (mRun) {
        Canvas c = null;
        try {
          c = mSurfaceHolder.lockCanvas(null);
          synchronized (mSurfaceHolder) {
            // if (mMode == STATE_RUNNING)
            updatePhysics();
            doDraw(c);
          }
        } finally {
          // do this in a finally so that if an exception is thrown
          // during the above, we don't leave the Surface in an
          // inconsistent state
          if (c != null) {
            mSurfaceHolder.unlockCanvasAndPost(c);
          }
        }
      }
    }

    /**
     * Used to signal the thread whether it should be running or not. Passing
     * true allows the thread to run; passing false will shut it down if it's
     * already running. Calling start() after this was most recently called with
     * false will result in an immediate shutdown.
     *
     * @param b
     *          true to run, false to shut down
     */
    public void setRunning(boolean b) {
      mRun = b;
    }

    /* Callback invoked when the surface dimensions change. */
    public void setSurfaceSize(int width, int height) {
      // synchronized to make sure these all change atomically
      synchronized (mSurfaceHolder) {
        mCanvasWidth = width;
        mCanvasHeight = height;
        int smallerSize = mCanvasHeight < mCanvasWidth ? mCanvasHeight
            : mCanvasWidth;
        mRadarDiameter = smallerSize - RADAR_MARGIN * 2;

        // don't forget to resize the background image
        // mBackgroundImage = Bitmap.createScaledBitmap(mBackgroundImage, width,
        // height, true);
      }
    }

    /**
     * Figures the lander state (x, y, fuel, ...) based on the passage of
     * realtime. Does not invalidate(). Called at the start of draw(). Detects
     * the end-of-game and sets the UI to the next state.
     */
    private void updatePhysics() {
      long now = System.currentTimeMillis();

      // Do nothing if mLastTime is in the future.
      // This allows the game-start to delay the start of the physics
      // by 100ms or whatever.
      if (mLastTime > now)
        return;

      float elapsed = (now - mLastTime) / 1000.0f;
      float elapsedSinceStart = (now - mStartTime) / 1000.0f;

      for (PolarPoint point : mPoints) {
        float oldAngle = point.getAngleDegrees();
        float newAngle = oldAngle + elapsed * TIME_MULTIPLIER;
        if (newAngle >= START_DEGREES) {
          point.increaseAngle(elapsed * TIME_MULTIPLIER);
        } else {
          if ((oldAngle + elapsedSinceStart * TIME_MULTIPLIER) >= START_DEGREES) {
            point.increaseAngle(elapsedSinceStart * TIME_MULTIPLIER);
          } else {
            break;
          }
        }
        // Log.d(TAG, "Angle: " + oldAngle + " --> " + point.getAngleDegrees());
      }

      while (!mPoints.isEmpty()
          && mPoints.peek().getAngleDegrees() > START_DEGREES + SWEEP_DEGREES) {
        mPoints.pop();
      }

      // mRotating -- update heading
      // if (mRotating != 0) {
      // mHeading += mRotating * (PHYS_SLEW_SEC * elapsed);
      //
      // // Bring things back into the range 0..360
      // if (mHeading < 0)
      // mHeading += 360;
      // else if (mHeading >= 360)
      // mHeading -= 360;
      // }

      // Base accelerations -- 0 for x, gravity for y
      // double ddx = 0.0;
      // double ddy = -PHYS_DOWN_ACCEL_SEC * elapsed;

      // if (mEngineFiring) {
      // // taking 0 as up, 90 as to the right
      // // cos(deg) is ddy component, sin(deg) is ddx component
      // double elapsedFiring = elapsed;
      // double fuelUsed = elapsedFiring * PHYS_FUEL_SEC;
      //
      // // tricky case where we run out of fuel partway through the
      // // elapsed
      // if (fuelUsed > mFuel) {
      // elapsedFiring = mFuel / fuelUsed * elapsed;
      // fuelUsed = mFuel;
      //
      // // Oddball case where we adjust the "control" from here
      // mEngineFiring = false;
      // }
      //
      // mFuel -= fuelUsed;
      //
      // // have this much acceleration from the engine
      // double accel = PHYS_FIRE_ACCEL_SEC * elapsedFiring;
      //
      // double radians = 2 * Math.PI * mHeading / 360;
      // ddx = Math.sin(radians) * accel;
      // ddy += Math.cos(radians) * accel;
      // }

      // double dxOld = mDX;
      // double dyOld = mDY;
      //
      // // figure speeds for the end of the period
      // mDX += ddx;
      // mDY += ddy;
      //
      // // figure position based on average speed during the period
      // mX += elapsed * (mDX + dxOld) / 2;
      // mY += elapsed * (mDY + dyOld) / 2;
      //
      mLastTime = now;
      //
      // // Evaluate if we have landed ... stop the game
      // double yLowerBound = TARGET_PAD_HEIGHT + mLanderHeight / 2
      // - TARGET_BOTTOM_PADDING;
      // if (mY <= yLowerBound) {
      // mY = yLowerBound;
      //
      // int result = STATE_LOSE;
      // CharSequence message = "";
      // Resources res = mContext.getResources();
      // double speed = Math.sqrt(mDX * mDX + mDY * mDY);
      // boolean onGoal = (mGoalX <= mX - mLanderWidth / 2 && mX + mLanderWidth
      // / 2 <= mGoalX + mGoalWidth);
      //
      // // "Hyperspace" win -- upside down, going fast,
      // // puts you back at the top.
      // if (onGoal && Math.abs(mHeading - 180) < mGoalAngle
      // && speed > PHYS_SPEED_HYPERSPACE) {
      // result = STATE_WIN;
      // mWinsInARow++;
      // doStart();
      //
      // return;
      // // Oddball case: this case does a return, all other cases
      // // fall through to setMode() below.
      // } else if (!onGoal) {
      // message = res.getText(R.string.message_off_pad);
      // } else if (!(mHeading <= mGoalAngle || mHeading >= 360 - mGoalAngle)) {
      // message = res.getText(R.string.message_bad_angle);
      // } else if (speed > mGoalSpeed) {
      // message = res.getText(R.string.message_too_fast);
      // } else {
      // result = STATE_WIN;
      // mWinsInARow++;
      // }
      //
      // setState(result, message);
      // }
    }

    /**
     * Draws the ship, fuel/speed bars, and background to the provided Canvas.
     */
    private void doDraw(Canvas canvas) {
      float polarCentreX = mCanvasWidth / 2;
      float polarCentreY = mCanvasHeight / 2;
      int radarWidth = mRadarDiameter - RADAR_INNER_RADIUS;

      // Draw the background image. Operations on the Canvas accumulate
      // so this is like clearing the screen.
      // canvas.drawBitmap(mBackgroundImage, 0, 0, null);
      canvas.drawPaint(mBackgroundPaint);

      // Green wedge
      int wedgeDiameter = RADAR_INNER_RADIUS + (radarWidth / 2);
      int left = (mCanvasWidth - wedgeDiameter) / 2;
      int top = (mCanvasHeight - wedgeDiameter) / 2;
      mScratchRect.set(left, top, left + wedgeDiameter, top + wedgeDiameter);
      canvas.drawArc(mScratchRect, 120, 300, true, mLinePaint);

      // Smaller wedge of background color to make a green arc
      wedgeDiameter -= 6;
      left = left + 3;
      top = top + 3;
      mScratchRect.set(left, top, left + wedgeDiameter, top + wedgeDiameter);
      canvas.drawArc(mScratchRect, 120, 300, true, mBackgroundPaint);

      for (PolarPoint point : mPoints) {
        if (point.getAngleDegrees() > START_DEGREES) {
          point.draw(polarCentreX, polarCentreY, radarWidth, canvas);
        }
      }

      // Draw the fuel gauge
      // int UI_BAR = 100;
      // int UI_BAR_HEIGHT = 10;
      // int fuelWidth = (int) (UI_BAR * 60 / 100);
      // mScratchRect.set(4, 4, 4 + fuelWidth, 4 + UI_BAR_HEIGHT);
      // canvas.drawRect(mScratchRect, mLinePaint);

      // Draw the speed gauge, with a two-tone effect
      // double speed = Math.sqrt(mDX * mDX + mDY * mDY);
      // int speedWidth = (int) (UI_BAR * speed / PHYS_SPEED_MAX);

      // if (speed <= mGoalSpeed) {
      // mScratchRect.set(4 + UI_BAR + 4, 4, 4 + UI_BAR + 4 + speedWidth,
      // 4 + UI_BAR_HEIGHT);
      // canvas.drawRect(mScratchRect, mLinePaint);
      // } else {
      // // Draw the bad color in back, with the good color in front of
      // // it
      // mScratchRect.set(4 + UI_BAR + 4, 4, 4 + UI_BAR + 4 + speedWidth,
      // 4 + UI_BAR_HEIGHT);
      // canvas.drawRect(mScratchRect, mLinePaintBad);
      // int goalWidth = (UI_BAR * mGoalSpeed / PHYS_SPEED_MAX);
      // mScratchRect.set(4 + UI_BAR + 4, 4, 4 + UI_BAR + 4 + goalWidth,
      // 4 + UI_BAR_HEIGHT);
      // canvas.drawRect(mScratchRect, mLinePaint);
      // }
      //
      // // Draw the landing pad
      // canvas.drawLine(mGoalX, 1 + mCanvasHeight - TARGET_PAD_HEIGHT, mGoalX
      // + mGoalWidth, 1 + mCanvasHeight - TARGET_PAD_HEIGHT, mLinePaint);
      //
      // // Draw the ship with its current rotation
      // canvas.save();
      // canvas.rotate((float) mHeading, (float) mX, mCanvasHeight - (float)
      // mY);
      // if (mMode == STATE_LOSE) {
      // mCrashedImage.setBounds(xLeft, yTop, xLeft + mLanderWidth, yTop
      // + mLanderHeight);
      // mCrashedImage.draw(canvas);
      // } else if (mEngineFiring) {
      // mFiringImage.setBounds(xLeft, yTop, xLeft + mLanderWidth, yTop
      // + mLanderHeight);
      // mFiringImage.draw(canvas);
      // } else {
      // mLanderImage.setBounds(xLeft, yTop, xLeft + mLanderWidth, yTop
      // + mLanderHeight);
      // mLanderImage.draw(canvas);
      // }
      canvas.restore();
    }

    /**
     * Starts the game, setting parameters for the current difficulty.
     */
    public void doStart() {
      synchronized (mSurfaceHolder) {
        mLastTime = System.currentTimeMillis() + 100;
        mStartTime = mLastTime;
        // setState(STATE_RUNNING);

        readAllThePoints();
      }
    }

    private void readAllThePoints() {
      BufferedReader reader = new BufferedReader(new InputStreamReader(mContext
          .getResources().openRawResource(R.raw.chrom1)));
      try {
        String line = reader.readLine();
        float firstPos = -1;
        // Pattern pattern =
        // Pattern.compile("([0-9]+)\\s+([0-9]+)\\s+([0-9\\.]+)");
        Pattern splitter = Pattern.compile("\\s+");
        while (line != null) {
          // //Log.d(TAG, "Trying to match on: " + line);
          // Matcher match = pattern.matcher(line);
          String[] groups = splitter.split(line);
          // long pos = Long.parseLong(match.group(2));
          // float value = Float.parseFloat(match.group(3));
          long pos = Long.parseLong(groups[1]);
          float value = Float.parseFloat(groups[2]);
          if (firstPos == -1) {
            firstPos = pos;
          }
          pos -= firstPos;
          // //Log.d(TAG, "Adding point for pos " + pos + " value " + value);
          mPoints.addLast(new PolarPoint(value, START_DEGREES
              - (pos * POS_TO_DEGREE_CONVERSION_FACTOR)));
          line = reader.readLine();
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
    }

  }

  private Context context;
  private GeneRadarThread thread;

  public GeneRadarView(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;

    SurfaceHolder holder = getHolder();
    holder.addCallback(this);

    thread = new GeneRadarThread(holder, context, new Handler() {
      @Override
      public void handleMessage(Message m) {
        // mStatusText.setVisibility(m.getData().getInt("viz"));
        // mStatusText.setText(m.getData().getString("text"));
      }
    });
  }

  /*
   * Callback invoked when the Surface has been created and is ready to be used.
   */
  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    // start the thread here so that we don't busy-wait in run()
    // waiting for the surface to be created
    thread.setRunning(true);
    thread.start();
  }

  /* Callback invoked when the surface dimensions change. */
  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
      int height) {
    thread.setSurfaceSize(width, height);
  }

  /*
   * Callback invoked when the Surface has been destroyed and must no longer be
   * touched. WARNING: after this method returns, the Surface/Canvas must never
   * be touched again!
   */
  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    // we have to tell thread to shut down & wait for it to finish, or else
    // it might touch the Surface after we return and explode
    boolean retry = true;
    thread.setRunning(false);
    while (retry) {
      try {
        thread.join();
        retry = false;
      } catch (InterruptedException e) {
      }
    }
  }

  /**
   * Fetches the animation thread corresponding to this LunarView.
   *
   * @return the animation thread
   */
  public GeneRadarThread getThread() {
    return thread;
  }

}
