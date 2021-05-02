package es.uniovi.wifidirect;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private List<WifiP2pDevice> peers;
    private AdapterListener adapterListener;

    public DeviceAdapter(List<WifiP2pDevice> peers, AdapterListener adapterListener){
        this.peers = peers;
        this.adapterListener = adapterListener;
    }
    @NonNull
    @Override
    public DeviceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list, parent, false);
        return new DeviceAdapter.ViewHolder(view, adapterListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        System.out.println(peers.get(position).deviceName);
        WifiP2pDevice device = peers.get(position);
        holder.nombres.setText(device.deviceName);
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }


    protected class ViewHolder extends  RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView nombres;
        AdapterListener adapterListener;
        public ViewHolder(View itemView,   AdapterListener adapterListener){
            super(itemView);
            nombres = itemView.findViewById(R.id.disp_name);
            this.adapterListener = adapterListener;
            itemView.setOnClickListener(this);

        }

        @Override
        public void onClick(View v) {
            adapterListener.onAdapterClick(getLayoutPosition());
        }
    }

    public interface AdapterListener{
        void onAdapterClick(int position);
    }

}


