/*
 * Copyright (C) 2026 HK Project
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

package com.android.settings.deviceinfo.aboutphone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.android.settings.R;

import java.util.List;

/**
 * Horizontal segmented storage bar showing per-category storage consumption inside About Phone.
 */
public class ColorfulRatioBar extends View {

    public static class RatioInfo {
        public final int type;
        public final int color;
        public final double ratio;

        public RatioInfo(int type, int color, double ratio) {
            this.type = type;
            this.color = color;
            this.ratio = ratio;
        }
    }

    private final RectF mBackgroundRect = new RectF();
    private final Path mClipPath = new Path();
    private Paint mPaint;
    private int mBackgroundColor;
    private float mRadius;
    private int mMinWidth;
    private int mWidth;
    private int mHeight;
    private List<RatioInfo> mRatioInfos;

    public ColorfulRatioBar(Context context) {
        this(context, null);
    }

    public ColorfulRatioBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mBackgroundColor = context.getColor(R.color.hk_storage_bar_track);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, getResources().getDisplayMetrics());
        mMinWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2f, getResources().getDisplayMetrics());
    }

    public void showRatioInfo(List<RatioInfo> infos) {
        mRatioInfos = infos;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        mBackgroundRect.set(0f, 0f, w, h);
        mClipPath.reset();
        mClipPath.addRoundRect(mBackgroundRect, mRadius, mRadius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mWidth <= 0 || mHeight <= 0) {
            return;
        }

        int save = canvas.save();
        canvas.clipPath(mClipPath);

        mPaint.setColor(mBackgroundColor);
        canvas.drawRect(0f, 0f, mWidth, mHeight, mPaint);

        if (mRatioInfos != null && !mRatioInfos.isEmpty()) {
            float cursor = 0f;
            for (RatioInfo info : mRatioInfos) {
                if (info.ratio <= 0.0) {
                    continue;
                }
                int segWidth = Math.max((int) (info.ratio * mWidth), mMinWidth);
                if (cursor + segWidth > mWidth) {
                    segWidth = (int) (mWidth - cursor);
                }
                if (segWidth <= 0) {
                    break;
                }
                mPaint.setColor(info.color);
                canvas.drawRect(cursor, 0f, cursor + segWidth, mHeight, mPaint);
                cursor += segWidth;
            }
        }

        canvas.restoreToCount(save);
    }
}
