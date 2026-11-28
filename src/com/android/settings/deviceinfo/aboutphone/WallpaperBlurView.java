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

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.settings.R;

/**
 * Displays the user's current wallpaper blurred with RenderEffect behind the About Phone hero card.
 */
public class WallpaperBlurView extends ImageView {

    public WallpaperBlurView(Context context) {
        this(context, null);
    }

    public WallpaperBlurView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WallpaperBlurView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initBlur();
    }

    private void initBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadWallpaper();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getDrawable() == null) {
            loadWallpaper();
        }
    }

    public void loadWallpaper() {
        Drawable wallpaper = null;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(getContext());
            if (wm != null) {
                wallpaper = wm.getWallpaperInfo() == null ? wm.getDrawable() : wm.getBuiltInDrawable();
                if (wallpaper == null) {
                    wallpaper = wm.getBuiltInDrawable();
                }
            }
        } catch (Throwable ignored) {
        }
        if (wallpaper != null) {
            setImageDrawable(wallpaper);
        } else {
            setImageResource(R.drawable.hk_about_header_fallback_bg);
        }
    }
}
