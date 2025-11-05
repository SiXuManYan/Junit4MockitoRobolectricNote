package com.panasonic.jp.lumixlab.controller.fragment.gallery.abs;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class FixedHeightBottomSheetDialog extends BottomSheetDialog {

    private final int fixedHeight;

    public FixedHeightBottomSheetDialog(@NonNull Context context, int theme, int fixedHeight) {
        super(context, theme);
        this.fixedHeight = fixedHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPeekHeight(fixedHeight);
        setMaxHeight(fixedHeight);
    }

    private void setPeekHeight(int peekHeight) {
        if (peekHeight <= 0) {
            return;
        }
        BottomSheetBehavior<View> behavior = getBottomSheetBehavior();
        if (behavior != null) {
            behavior.setPeekHeight(peekHeight);
        }
    }

    private void setMaxHeight(int maxHeight) {
        if (maxHeight <= 0) {
            return;
        }
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    public BottomSheetBehavior<View> getBottomSheetBehavior() {
        Window window = getWindow();
        if (window == null) {
            return null;
        }
        View view = window.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (view != null) {
            return BottomSheetBehavior.from(view);
        }
        return null;
    }
}