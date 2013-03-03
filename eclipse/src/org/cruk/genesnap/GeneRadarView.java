package org.cruk.genesnap;

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GeneRadarView extends SurfaceView implements
    SurfaceHolder.Callback {

  private static final String TAG = GeneRadarView.class.getName();

  private static final int ACTION_SCORE = 1;
  private static final int ACTION_FINISH = 2;

  private static class Selection {
    public float startOffset;
    public float endOffset;
    public float height;

    public Selection(float startOffset, float endOffset, float height) {
      super();
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.height = height;
    }
  }

  static class GeneRadarThread extends Thread {

    private static final int TIME_MULTIPLIER = 70;

    private static final float POS_CONVERSION_FACTOR = 0.00002f;

    private static final int STATE_READY = 0;
    private static final int STATE_RUNNING = 1;

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

    /** Used to figure out elapsed time between frames */
    private long mLastTime;

    private long mStartTime;
    private float mCanvasOffset;

    /** Message handler used by thread to interact with TextView */
    private Handler mHandler;

    /** Indicate whether the surface has been created & is ready to draw */
    private boolean mRun = false;

    private int mMode = STATE_READY;

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

    private Paint mSelectionPaint;

    /**
     * Points to draw on the canvas as paris of float. X followed by Y
     * coordinate.
     */
    private float[] mPoints;

    /** Selections the user has entered already */
    private List<Selection> mSelections;

    /** Maintained while the user is dragging and creating a new Selection */
    private Selection mSelectionInProgress;

    private int mPointsOffset = 0;

    public GeneRadarThread(SurfaceHolder surfaceHolder, Context context,
        Handler handler) {
      // get handles to some important objects
      mSurfaceHolder = surfaceHolder;
      mHandler = handler;
      mContext = context;

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

      mPointPaint = new Paint();
      // mPointPaint.setAntiAlias(true);
      mPointPaint.setStrokeWidth(4);
      mPointPaint.setColor(res.getColor(android.R.color.holo_blue_light));

      mBackgroundPaint = new Paint();
      mBackgroundPaint.setAntiAlias(true);
      mBackgroundPaint.setColor(res.getColor(android.R.color.background_light));

      mSelectionPaint = new Paint();
      mSelectionPaint.setStrokeWidth(10);
      mSelectionPaint.setColor(res.getColor(android.R.color.holo_red_dark));

      mScratchRect = new RectF(0, 0, 0, 0);

      mSelections = new LinkedList<Selection>();
    }

    @Override
    public void run() {
      while (mRun) {
        Canvas c = null;
        try {
          c = mSurfaceHolder.lockCanvas(null);
          synchronized (mSurfaceHolder) {
            if (mMode == STATE_RUNNING) {
              updatePhysics();
            }
            if (mRun && mMode == STATE_RUNNING) {
              doDraw(c);
            }
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
        // int oldWidth = mCanvasWidth;
        // int oldHeight = mCanvasHeight;
        mCanvasWidth = width;
        mCanvasHeight = height;

        // for (int i = mPointsOffset; i < mPoints.length; i++) {
        // mPoints[i] = (mPoints[i] / oldWidth) * width;
        // i++;
        // mPoints[i] = (mPoints[i] / oldHeight) * height;
        // }

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

      float elapsedTimeSinceStart = (now - mStartTime) / 1000.0f;
      mCanvasOffset = elapsedTimeSinceStart * TIME_MULTIPLIER;

      float x;
      for (int i = mPointsOffset; i < mPoints.length - 1; i += 2) {
        x = mCanvasWidth - mCanvasOffset + mPoints[i] * POS_CONVERSION_FACTOR;
        if (x < 0) {
          mPointsOffset += 2;
        } else {
          break;
        }
      }

      if (mPointsOffset > mPoints.length - 1) {
        // We ran out of stuff to show
        setRunning(false);
        mMode = STATE_READY;
        Message msg = mHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt("action", ACTION_FINISH);
        msg.setData(b);
        mHandler.sendMessage(msg);
      }

      mLastTime = now;

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
      // Draw the background image. Operations on the Canvas accumulate
      // so this is like clearing the screen.
      // canvas.drawBitmap(mBackgroundImage, 0, 0, null);
      canvas.drawPaint(mBackgroundPaint);

      float x, y;
      for (int i = mPointsOffset; i < mPoints.length - 1; i += 2) {
        x = mCanvasWidth - mCanvasOffset + mPoints[i] * POS_CONVERSION_FACTOR;
        y = mPoints[i + 1] * mCanvasHeight;
        if (x > mCanvasWidth) {
          break;
        }
        canvas.drawPoint(x, y, mPointPaint);
      }

      int score = 0;
      for (int i = 0; i < mSelections.size(); i++) {
        Selection selection = mSelections.get(i);
        drawSelection(canvas, selection);
        score += selection.endOffset - selection.startOffset;
      }
      Message msg = mHandler.obtainMessage();
      Bundle b = new Bundle();
      b.putInt("action", ACTION_SCORE);
      b.putInt("score", score);
      msg.setData(b);
      mHandler.sendMessage(msg);

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

    private void drawSelection(Canvas canvas, Selection selection) {
      float startX = mCanvasWidth - mCanvasOffset + selection.startOffset;
      float endX = mCanvasWidth - mCanvasOffset + selection.endOffset;
      if (startX < mCanvasWidth || endX > 0) {
        float Y = selection.height * mCanvasHeight;
        float mirrorY = (1 - selection.height) * mCanvasHeight;
        canvas.drawLine(startX, Y, endX, Y, mSelectionPaint);
        canvas.drawLine(startX, mirrorY, endX, mirrorY, mSelectionPaint);
      }
    }

    public void setPoints(float[] points) {
      mPoints = points;
    }

    /**
     * Starts the game, setting parameters for the current difficulty.
     */
    public void doStart() {
      synchronized (mSurfaceHolder) {
        mLastTime = System.currentTimeMillis() + 100;
        mStartTime = mLastTime;
        mMode = STATE_RUNNING;
      }
    }

    public boolean doTouchEvent(MotionEvent event) {
      // Log.d(TAG, "doTouchEvent. event: " + event.toString());
      float x = event.getX();
      float y = event.getY() - 10;
      if (x < 0 || y < 0 || x > mCanvasWidth || y > mCanvasHeight) {
        return false;
      }
      float offset = mCanvasOffset - mCanvasWidth + x;
      float height = y / mCanvasHeight;
      // Log.d(TAG, "doTouchEvent. offset: " + offset + " height: " + height);
      switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        // Log.d(TAG, "doTouchEvent. ACTION_DOWN");
        mSelectionInProgress = new Selection(offset, offset, height);
        mSelections.add(mSelectionInProgress);
        break;
      case MotionEvent.ACTION_MOVE:
        // Log.d(TAG, "doTouchEvent. ACTION_MOVE");
        mSelectionInProgress.endOffset = offset;
        break;
      case MotionEvent.ACTION_UP:
        // Log.d(TAG, "doTouchEvent. ACTION_UP");
        mSelectionInProgress.endOffset = offset;
        mSelectionInProgress = null;
        break;
      default:
        return false;
      }
      return true;
    }

  }

  private GeneRadarThread mThread;
  private Callback mCallback;

  public GeneRadarView(Context context, AttributeSet attrs) {
    super(context, attrs);

    SurfaceHolder holder = getHolder();
    holder.addCallback(this);

    mThread = new GeneRadarThread(holder, context, new Handler() {
      @Override
      public void handleMessage(Message m) {
        int action = m.getData().getInt("action");
        switch(action) {
        case ACTION_SCORE:
          if (mCallback != null)
            mCallback.updateScore(m.getData().getInt("score"));
          break;
        case ACTION_FINISH:
          if (mCallback != null)
            mCallback.endGame();
          break;
        }
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
    mThread.setRunning(true);
    mThread.start();
  }

  /* Callback invoked when the surface dimensions change. */
  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
      int height) {
    mThread.setSurfaceSize(width, height);
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
    mThread.setRunning(false);
    while (retry) {
      try {
        mThread.join();
        retry = false;
      } catch (InterruptedException e) {
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // Log.d(TAG, "onTouchEvent. event: " + event.toString());
    return mThread.doTouchEvent(event);
  }

  /**
   * Fetches the animation thread corresponding to this LunarView.
   *
   * @return the animation thread
   */
  public GeneRadarThread getThread() {
    return mThread;
  }

  public void setCallback(Callback callback) {
    mCallback = callback;
  }

  public static interface Callback {
    public void updateScore(int newScore);
    public void endGame();
  }
}
