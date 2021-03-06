package xzr.hkf;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class Worker extends MainActivity.fileWorker{
    Activity activity;
    String file_path;
    String binary_path;
    boolean is_error;
    public Worker(Activity activity){
        this.activity=activity;
    }
    public void run(){
        MainActivity.cur_status= MainActivity.status.flashing;
        ((MainActivity)activity).update_title();
        is_error=false;
        file_path=activity.getFilesDir().getAbsolutePath()+"/anykernel.zip";
        binary_path=activity.getFilesDir()+"/META-INF/com/google/android/update-binary";

        try {
            cleanup();
        } catch (IOException ioException) {
            is_error=true;
        }
        if(is_error){
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_cleanup),activity);
            MainActivity.cur_status= MainActivity.status.error;
            ((MainActivity)activity).update_title();
            return;
        }

        try{
            copy();
        }catch (IOException ioException){
            is_error=true;
        }
        if(!is_error)
            is_error=!new File(file_path).exists();
        if(is_error){
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_copy),activity);
            MainActivity.cur_status= MainActivity.status.error;
            ((MainActivity)activity).update_title();
            return;
        }

        try{
            getBinary();
        }catch (IOException ioException){
            is_error=true;
        }
        if(is_error){
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_get_exe), activity);
            MainActivity.cur_status= MainActivity.status.error;
            ((MainActivity)activity).update_title();
            return;
        }

        try{
            flash(activity);
        }catch (IOException ioException){
            is_error=true;
        }
        if(is_error){
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_root), activity);
            MainActivity.cur_status= MainActivity.status.error;
            ((MainActivity)activity).update_title();
            return;
        }
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.reboot_complete_title)
                    .setMessage(R.string.reboot_complete_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        try {
                            reboot();
                        } catch (IOException e) {
                            Toast.makeText(activity,R.string.failed_reboot,Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.no,null)
                    .create().show();
        });
        MainActivity.cur_status= MainActivity.status.flashing_done;
        ((MainActivity)activity).update_title();
    }

    void copy() throws IOException {
        InputStream inputStream = activity.getContentResolver().openInputStream(uri);
        FileOutputStream fileOutputStream = new FileOutputStream(new File(file_path));
        byte[] buffer = new byte[1024];
        int count=0;
        while((count=inputStream.read(buffer))!=-1) {
            fileOutputStream.write(buffer, 0, count);
        }
        fileOutputStream.flush();
        inputStream.close();
        fileOutputStream.close();
    }

    void getBinary() throws IOException {
        Process process=new ProcessBuilder("sh").start();
        BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
        OutputStreamWriter outputStreamWriter=new OutputStreamWriter(process.getOutputStream());
        outputStreamWriter.write("unzip "+file_path+" \"*/update-binary\" -d "+activity.getFilesDir()+"\nexit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine()!=null){}
        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();
        if(!new File(binary_path).exists())
            throw new IOException();
    }

    void flash(Activity activity) throws IOException {
        Process process=new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter=new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("export POSTINSTALL="+activity.getFilesDir()+"\n");
        outputStreamWriter.write("sh "+binary_path+" 3 1 "+file_path+"&& touch "+activity.getFilesDir()+"/done\nexit\n");
        outputStreamWriter.flush();
        String line;
        while((line=bufferedReader.readLine())!=null)
            MainActivity.appendLog(line,activity);

        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        if(!new File(activity.getFilesDir()+"/done").exists())
            throw new IOException();
    }

    void cleanup() throws IOException {
        Process process=new ProcessBuilder("sh").start();
        BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
        OutputStreamWriter outputStreamWriter=new OutputStreamWriter(process.getOutputStream());
        outputStreamWriter.write("rm -rf "+activity.getFilesDir()+"/*\nexit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine()!=null){}
        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();
    }

    void reboot() throws IOException{
        Process process=new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter=new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("svc power reboot\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while(bufferedReader.readLine()!=null){}
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

}
