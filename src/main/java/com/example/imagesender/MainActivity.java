package com.example.imagesender;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private Thread th = null;
    private File cFolder=null;
    private final static int RESULT_CAMERA = 1000;
    private File cameraFile;
    private Uri cameraUri;
    private InputStream inputStream;
    private static Context context;
    private boolean isContinued=false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.option, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.menuItem1:
                Intent intent = new Intent(this, Settings.class);
                startActivity(intent);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MainActivity.context = getApplicationContext();
        cFolder =
                getExternalFilesDir(Environment.DIRECTORY_DCIM +
                        "/imageSender");
        if(!cFolder.exists()){
            cFolder.mkdir();
        }

    }
    //ボタンクリック時
    public void onClicked(View view){
        String fileDate = new SimpleDateFormat(
                "ddHHmmss", Locale.US).format(new Date());
        String fileName = String.format("CameraIntent_%s.jpg", fileDate);
        cameraFile = new File(cFolder, fileName);
        cameraUri = FileProvider.getUriForFile(
                MainActivity.this,
                getApplicationContext().getPackageName() +
                        ".fileprovider",
                cameraFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        if(intent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(intent, RESULT_CAMERA);
        }
    }
    //カメラで撮ったあと
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RESULT_CAMERA){
            if(cameraUri == null){
                Log.d("debug", "cancel?");
                return;
            }else{
                try {
                    inputStream = this.getContentResolver()
                            .openInputStream(cameraUri);
                    SharedPreferences pref = getSharedPreferences(getPackageName()+"_preferences", MODE_PRIVATE);
                    if(pref == null){
                        Log.d("test", "pref null");
                    }
                    String way = pref.getString("sendingWay", "aa");
                    String ipAddress = pref.getString("ipAddress", "aa");
                    Log.d("test", way);
                    Log.d("test", ipAddress);
                    if(way.equals("Bluetooth")) {

                        Log.d("test", "reached");
                        String BTDeviceAddress = pref.getString("BTDeviceAddress",
                                "");
                        String BTUUID = pref.getString("BTUUID",
                                "");
                        senderByBluetooth(BTDeviceAddress, BTUUID);
                    }else {
                        String ip = pref.getString("ipAddress", "192.168.10.101");
                        int portN = Integer.parseInt(pref.getString("portN", "15403"));
                        senderByTcpip(ip, portN);
                    }
                    if(th != null){
                        try{
                            th.join();
                            th = null;
                        }catch(InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                }catch(FileNotFoundException e){
                        Log.d("ERROR", "No image");

                }finally{
                    if(inputStream != null){
                        try{
                            inputStream.close();
                        }catch(IOException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    //送信する(TCPIP)
    public void senderByTcpip(final String IpAddress, final int portN){

        Runnable sender = new Runnable(){
            @Override
            public void run(){
                Socket socket = null;
                OutputStream os;
                DataOutputStream dos = null;
                try{
                    socket = new Socket(IpAddress, portN);
                    os = socket.getOutputStream();
                    dos = new DataOutputStream(os);
                    sending(inputStream, dos);
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch(IOException e) {
                    Log.d("ERROR","Error in sending.");
                    e.printStackTrace();
                }finally{
                    if(socket != null){
                        try{
                            socket.close();
                            socket = null;
                        }catch(IOException e){
                            e.printStackTrace();
                            Log.d("ERROR", "Unable to close socket.");
                        }}
                    if(dos != null){
                        try{
                            dos.close();
                        }catch(IOException e){
                            Log.d("ERROR", "Unable to close dos.");
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        Thread th = new Thread(sender);
        th.start();
        try {
            th.join();
            final Toast toastDone = Toast.makeText(this, "Done.",
                    Toast.LENGTH_LONG);
        }catch(InterruptedException e){
            e.printStackTrace();
            Log.d("ERROR", "cannot join");
        }
    }
    public void senderByBluetooth(final String BTDeviceAddress,
                                  final String BTUUID){
        final int REQUEST_CODE_ENABLE_BT = 1;

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            Log.d("ERROR", "No bluetooth support.");
            return;
        }else if (! adapter.isEnabled()) {
            Log.d("ERROR", "Bluetooth is disabled.");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_CODE_ENABLE_BT);
            return;
        }
        Runnable sender = new Runnable() {
            @Override
            public void run() {
                BluetoothDevice server = adapter.getRemoteDevice(BTDeviceAddress);
                BluetoothSocket socket = null;
                OutputStream os;
                DataOutputStream dos = null;
                try {
                    socket = server.createRfcommSocketToServiceRecord(UUID.fromString(BTUUID));
                    try {
                        socket.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d("ERROR", "Unable to connect.");
                        return;
                    }
                    try {
                        os = socket.getOutputStream();
                        dos = new DataOutputStream(os);
                        sending(inputStream, dos);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d("ERROR", "Unable to create output stream.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d("ERROR", "Unable to close socket.");
                        }
                    }
                    if (dos != null) {
                        try {
                            dos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d("ERROR", "Unable to close output stream.");
                        }
                    }
                }
            }
        };
        th = new Thread(sender);
        th.start();
    }
    //送る部分
    public void sending(InputStream in, OutputStream os){
        SharedPreferences pref = getSharedPreferences(getPackageName()+"_preferences", MODE_PRIVATE);
        String isResized = pref.getString("isResized", "４分の１に");
        try{
            switch (isResized) {
                case "４分の１に":
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver()
                            , cameraUri);
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                            bitmap, width / 4, height / 4, true
                    );
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                    break;
                default:
                    int size = in.available();
                    byte[] buffer = new byte[size];
                    while (true) {
                        int len = inputStream.read(buffer);
                        if (len < 0) {
                            break;
                        }
                    }
                    os.write(buffer);
            }
            os.flush();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    public void delete(View v){
        new AlertDialog.Builder(this)
                .setTitle("削除確認")
                .setMessage("送信した画像ファイルを全て削除します。")
                .setPositiveButton("OK", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(
                            DialogInterface dialog, int which){
                        Log.d("test", "test1");
                        cFolder =
                                getExternalFilesDir(Environment.DIRECTORY_DCIM +
                                        "/imageSender");
                        if(cFolder.exists()){
                            Log.d("test", "test1");
                            File[] files = cFolder.listFiles();
                            if(files != null){
                                Log.d("test", "test2");
                                for(int i=0; i < files.length; i++){
                                    if(files[i].exists()){
                                        String name = files[i].getName();
                                        String extension = name.substring(name
                                        .lastIndexOf("."));
                                        if(extension.equals(".jpg")){
                                            files[i].delete();
                                        }
                                    }
                                }
                            }
                        }


                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        Toast.makeText(this, "Finished!", Toast.LENGTH_LONG).show();
    }
    public static Context getAppContext() {
        return MainActivity.context;
    }

}
