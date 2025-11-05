package com.panasonic.jp.lumixlab.controller.fragment.gallery.abs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.panasonic.jp.lumixlab.R;

public abstract class CommonBottomSheetDialogFragment<VB extends ViewBinding> extends BottomSheetDialogFragment {

    protected VB viewBinding;
    private int fixedHeight = 0;
    protected Context context;

    /**
     * Set a fixed height (unit: px)
     * @param height The height. Passing 0 will not set the height.
     */
    public void setFixedHeight(int height) {
        this.fixedHeight = height;
    }

    protected abstract VB getViewBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogThemeLight);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        if (fixedHeight > 0) {
            return new FixedHeightBottomSheetDialog(context, getTheme(), fixedHeight);
        } else {
            return new com.google.android.material.bottomsheet.BottomSheetDialog(context, getTheme());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Disable soft keyboard from lifting layout
        if (getActivity() != null) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        viewBinding = getViewBinding(inflater, container);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewOperations();
        initData();
        initListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    protected abstract void initViewOperations();

    protected abstract void initData();

    protected abstract void initListener();
}