import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public abstract class CommonAdapter<T, VB extends ViewBinding>
        extends RecyclerView.Adapter<CommonAdapter<T, VB>.BindingViewHolder> {

    protected List<T> dataList = new ArrayList<>();
    private OnItemClickListener<T> onItemClickListener;
    private OnItemChildClickListener<T> onItemChildClickListener;
    protected BindingViewHolder currentHolder;

    public CommonAdapter() {
    }

    public CommonAdapter(List<T> dataList) {
        this.dataList = dataList;
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemChildClickListener(OnItemChildClickListener<T> listener) {
        this.onItemChildClickListener = listener;
    }

    @NonNull
    @Override
    public BindingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        VB binding = onCreateBinding(LayoutInflater.from(parent.getContext()), parent, viewType);
        return new BindingViewHolder(binding, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull BindingViewHolder holder, int position) {
        T item = dataList.get(position);
        currentHolder = holder;

        onBind(holder.binding, item, position, holder.viewType);

        // item click
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(item, position);
            }
        });

        // item child click
        for (View childClickView : holder.childClickViews) {
            childClickView.setOnClickListener(v -> {
                if (onItemChildClickListener != null) {
                    onItemChildClickListener.onItemChildClick(item, position, v, v.getId());
                }
            });
        }
    }

    protected abstract VB onCreateBinding(LayoutInflater inflater, ViewGroup parent, int viewType);

    protected abstract void onBind(VB binding, T item, int position, int viewType);

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }


    @SuppressLint("NotifyDataSetChanged")
    public void setNewDataList(List<T> list) {
        this.dataList.clear();
        this.dataList.addAll(list);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setDataList(List<T> list) {
        this.dataList = list;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addDataList(List<T> list) {
        if (list != null) {
            this.dataList.addAll(list);
            notifyDataSetChanged();
        }
    }

    public interface OnItemClickListener<T> {
        void onItemClick(T item, int position);
    }

    public interface OnItemChildClickListener<T> {
        void onItemChildClick(T item, int position, View view, int viewId);
    }

    class BindingViewHolder extends RecyclerView.ViewHolder {
        VB binding;
        ArrayList<View> childClickViews = new ArrayList<>();
        int viewType;

        public BindingViewHolder(@NonNull VB binding, int viewType) {
            super(binding.getRoot());
            this.binding = binding;
            this.viewType = viewType;
        }

        public void bindChildClickListener(@NonNull View... views) {
            childClickViews.addAll(Arrays.asList(views));
        }
    }

    protected void bindChildClickListener(View... views) {
        if (currentHolder != null) {
            currentHolder.bindChildClickListener(views);
        }
    }
}
