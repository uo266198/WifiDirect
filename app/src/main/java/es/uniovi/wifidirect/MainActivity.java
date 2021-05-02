package es.uniovi.wifidirect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Build;
import android.os.Bundle;
import android.net.wifi.p2p.WifiP2pManager.*;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.chrono.IsoEra;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity implements ChannelListener, DeviceAdapter.AdapterListener {

    Button btnBusqueda;
    Button btn_send;
    EditText texto;

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;

    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();

    static final int MESSAGE_READ = 1;

    Servidor servidor;
    Cliente  cliente;
    IntercambioMSG intercambioMSG;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inicializar();
    }

    // Se encarga de leer los mensajes y escribirlos en el cuadro de texto.
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    texto.setText(tempMsg);
                    break;
            }
            return true;
        }
    });


    private class IntercambioMSG extends Thread{
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public IntercambioMSG(Socket sock) throws IOException {
            socket = sock;
            inputStream=  socket.getInputStream();
            outputStream = socket.getOutputStream();
        }
        @Override
        public void run(){
            byte[] buffer = new byte [1024];
            int bytes;

            while(socket!=null){
                try {
                    bytes = inputStream.read(buffer);
                    if(bytes>0){
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) throws IOException {
            outputStream.write(bytes);
        }
    }

    @Override
    public void onChannelDisconnected() {

    }


    // Inicializamos los botones, cuadros de texto, el canal , el receptor y el manage Wifip2p etc.
    private void inicializar() {
        texto =findViewById(R.id.edtxt_texto);
        btn_send = findViewById(R.id.btn_send);
        btnBusqueda = findViewById(R.id.button_busqueda);
        btnBusqueda.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                manager.discoverPeers(channel, new ActionListener() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(int reason) {

                    }
                });
            }
        });


        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        receiver = new WDBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    // ACtualizamos la lista de peers.
    WifiP2pManager.PeerListListener peerListListener = new PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (peerList.getDeviceList() != peers) {
                peers.clear();
                peers.addAll(peerList.getDeviceList());
            }
            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(), "No se ha encontrado ningún dispositivo", Toast.LENGTH_SHORT).show();
            }
            DeviceAdapter adapter = new DeviceAdapter(peers, MainActivity.this::onAdapterClick);

            RecyclerView rview = findViewById(R.id.lista_devices);
            rview.setLayoutManager(new LinearLayoutManager(rview.getContext()));
            rview.setAdapter(adapter);


        }
    };

    // Si ha habido una conexion, la informacióon llega a esta clase, se encarga de definir quien es host y quien servidor y lanzar
    // servidor o cliente según se tenga en cuenta.
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener(){

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            final InetAddress groupowner = info.groupOwnerAddress;
            if(info.groupFormed && info.isGroupOwner){
                servidor = new Servidor();
                servidor.start();

            }
            else if (info.groupFormed){
                cliente = new Cliente (groupowner);
                cliente.start();
            }
        }
    };


    @Override
    public void onAdapterClick(int position) {
        WifiP2pDevice device = peers.get(position);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess(){
                Toast.makeText(getApplicationContext(), "Conectado a "+ device.deviceName,Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Error conectandose a "+ device.deviceName,Toast.LENGTH_SHORT).show();
            }
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mensaje = texto.getText().toString();
                byte[] bytes = mensaje.getBytes();
                try {
                    intercambioMSG.write(mensaje.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Clase servidor
    public class Servidor extends Thread{
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run(){
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                intercambioMSG = new IntercambioMSG(socket);
                intercambioMSG.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //CLase cliente
    public class Cliente extends Thread{
        Socket socket;
        String dirHost;

        public Cliente (InetAddress direccionHost){
            dirHost = direccionHost.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run(){
            try {
                socket.connect(new InetSocketAddress(dirHost, 8888),500);
                intercambioMSG = new IntercambioMSG(socket);
                intercambioMSG.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}