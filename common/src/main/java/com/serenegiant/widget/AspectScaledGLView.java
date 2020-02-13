package com.serenegiant.widget;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2020 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.content.Context;
import android.content.res.TypedArray;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;

import com.serenegiant.common.R;
import com.serenegiant.graphics.MatrixUtils;

import androidx.annotation.Nullable;

/**
 * View/表示内容のスケーリング処理を追加したGLView
 * スケーリングモードがSCALE_MODE_KEEP_ASPECTのときはViewのサイズ変更を行う
 */
public class AspectScaledGLView extends GLView
	implements IAspectRatioView, IScaledView {

	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = AspectScaledGLView.class.getSimpleName();

	@ScaleMode
	private int mScaleMode;
	private double mRequestedAspect;		// initially use default window size

	/**
	 * コンストラクタ
	 * @param context
	 */
	public AspectScaledGLView(@Nullable final Context context) {
		this(context, null, 0);
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 */
	public AspectScaledGLView(@Nullable final Context context,
		@Nullable final AttributeSet attrs) {

		this(context, attrs, 0);
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 * @param defStyleAttr
	 */
	public AspectScaledGLView(@Nullable final Context context,
		@Nullable final AttributeSet attrs, final int defStyleAttr) {

		super(context, attrs, defStyleAttr);
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		final TypedArray a = context.getTheme().obtainStyledAttributes(
			attrs, R.styleable.AspectScaledGLView, defStyleAttr, 0);
		try {
			mRequestedAspect = a.getFloat(R.styleable.AspectScaledGLView_aspect_ratio, -1.0f);
			mScaleMode = a.getInt(R.styleable.AspectScaledGLView_scale_mode, SCALE_MODE_KEEP_ASPECT);
		} finally {
			a.recycle();
		}
	}

	/**
	 * アスペクト比を保つように大きさを決める
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (DEBUG) Log.v(TAG, "onMeasure:mRequestedAspect=" + mRequestedAspect);
		// 要求されたアスペクト比が負の時(初期生成時)は何もしない
		if (mRequestedAspect > 0 && (mScaleMode == SCALE_MODE_KEEP_ASPECT)) {
			int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
			int initialHeight = MeasureSpec.getSize(heightMeasureSpec);
			final int horizPadding = getPaddingLeft() + getPaddingRight();
			final int vertPadding = getPaddingTop() + getPaddingBottom();
			initialWidth -= horizPadding;
			initialHeight -= vertPadding;

			final double viewAspectRatio = (double)initialWidth / initialHeight;
			final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

			// 計算誤差が生じる可能性が有るので指定した値との差が小さければそのままにする
			if (Math.abs(aspectDiff) > 0.005) {
				if (aspectDiff > 0) {
					// 幅基準で高さを決める
					initialHeight = (int) (initialWidth / mRequestedAspect);
				} else {
					// 高さ基準で幅を決める
					initialWidth = (int) (initialHeight * mRequestedAspect);
				}
				initialWidth += horizPadding;
				initialHeight += vertPadding;
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
			}
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private int prevWidth = -1;
	private int prevHeight = -1;
	@Override
	protected void onLayout(final boolean changed,
		final int left, final int top, final int right, final int bottom) {

		super.onLayout(changed, left, top, right, bottom);
		final int width = right - left;
		final int height = bottom - top;
		if (DEBUG) Log.v(TAG, String.format("onLayout:(%dx%d)", width, height));
		if ((width == 0) || (height == 0)) return;

		if ((prevWidth != width) || (prevHeight != height)) {
			prevWidth = width;
			prevHeight = height;
			onResize(width, height);
		}
		init();
	}

	protected void onResize(final int width, final int height) {
	}

//================================================================================
// IAspectRatioView
//================================================================================
	/**
	 * アスペクト比を設定する。アスペクト比=<code>幅 / 高さ</code>.
	 */
	@Override
	public void setAspectRatio(final double aspectRatio) {
		if (DEBUG) Log.v(TAG, "setAspectRatio");
		if (mRequestedAspect != aspectRatio) {
			mRequestedAspect = aspectRatio;
			requestLayout();
		}
 	}

	/**
	 * アスペクト比を設定する
	 * @param width
	 * @param height
	 */
	@Override
	public void setAspectRatio(final int width, final int height) {
		if ((width > 0) && (height > 0)) {
			setAspectRatio(width / (double)height);
		}
	}

	@Override
	public double getAspectRatio() {
		return mRequestedAspect;
	}

//================================================================================
// IScaledView
//================================================================================

	@Override
	public void setScaleMode(@ScaleMode final int scaleMode) {
		if (DEBUG) Log.v(TAG, "setScaleMode:" + scaleMode);
		if (mScaleMode != scaleMode) {
			mScaleMode = scaleMode;
			requestLayout();
		}
	}

	@ScaleMode
	@Override
	public int getScaleMode() {
		return mScaleMode;
	}

//================================================================================
// 実際の実装
//================================================================================
	/**
	 * 拡大縮小回転状態をリセット
	 */
	protected void init() {
		final int viewWidth = getWidth();
		final int viewHeight = getHeight();
		final double videoWidth = mRequestedAspect > 0 ? mRequestedAspect * viewHeight : viewHeight;
		final double videoHeight = viewHeight;
		if (DEBUG) Log.v(TAG, String.format("init:(%dx%d),mScaleMode=%d",
			viewWidth ,viewHeight, mScaleMode) );
		// apply matrix
		final float[] transform = new float[16];
		Matrix.setIdentityM(transform, 0);
		switch (mScaleMode) {
		case SCALE_MODE_STRETCH_TO_FIT:	// これは引き伸ばすので何もしない
			// 何もしない
			break;
		case SCALE_MODE_KEEP_ASPECT:
		case SCALE_MODE_CROP: // FIXME もう少し式を整理できそう
			final double scaleX = viewWidth / videoWidth;
			final double scaleY = viewHeight / videoHeight;
			final double scale = (mScaleMode == SCALE_MODE_CROP)
				? Math.max(scaleX,  scaleY)	// SCALE_MODE_CROP
				: Math.min(scaleX, scaleY);	// SCALE_MODE_KEEP_ASPECT
			final float width = (float)(scale * videoWidth);
			final float height = (float)(scale * videoHeight);
			if (DEBUG) Log.v(TAG, String.format("size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)",
				width, height, scaleX, scaleY, width / viewWidth, height / viewHeight));
			Matrix.scaleM(transform, 0,
				width / viewWidth, height / viewHeight, 1.0f);
			break;
		}
		if (DEBUG) Log.v(TAG, "init:" + MatrixUtils.toGLMatrixString(transform));
		setTransform(transform);
	}
}