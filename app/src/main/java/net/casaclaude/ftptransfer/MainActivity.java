package net.casaclaude.ftptransfer;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.Timestamp;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private boolean isFileExists(String filename){
        File folder1 = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + filename);
        return folder1.exists();
    }

    private boolean removeFile(String filename){
        File folder1 = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + filename);
        return folder1.delete();
    }

    private void writeToFile(String data) {
        try {
            String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            String fileName = "ftptransfer.txt";
            File myFile = new File(baseDir + File.separator + fileName);

            boolean b = myFile.createNewFile();

            FileOutputStream fileOutputStream = new FileOutputStream(myFile);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private String readUrl() {
        File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard,"ftptransfer-url.txt");

        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File read failed: " + e.toString());
        }

        return text.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ftptransfer-url.txt : http://mirror.ovh.net/ftp.ubuntu.com/ls-lR.gz
        String url = readUrl();

        if (isFileExists("download-trash.dat")) { removeFile("download-trash.dat"); }

        final TextView tv=(TextView)findViewById(R.id.myText);
        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

        request.setDescription("ftp download @ " + url);
        request.setTitle("download");
        request.setDestinationInExternalPublicDir(Environment.getExternalStorageDirectory().getAbsolutePath(), "download-trash.dat");

        // get download service and enqueue file
        final DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentStatus = DownloadManager.STATUS_RUNNING;
                long downloadId = downloadManager.enqueue(request);
                long downloadedBytes = 0;
                long totalDownloadedBytes = 0;
                long startingTime = System.currentTimeMillis();
                long downloadSpeed;
                long timeElapsed;

                while (currentStatus != DownloadManager.STATUS_FAILED) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);

                    Cursor cursor = downloadManager.query(q);
                    cursor.moveToFirst();

                    if (downloadedBytes != cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)) && downloadedBytes < cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))) {
                        timeElapsed = System.currentTimeMillis() - startingTime + 1;
                        totalDownloadedBytes = totalDownloadedBytes + cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)) - downloadedBytes;
                        downloadSpeed = totalDownloadedBytes / timeElapsed * 1000;

                        downloadedBytes = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

                        final String downloadData = "{\"downloadedSoFar\": \""
                                + downloadedBytes + "\", \"downloadSpeed\": \""
                                + downloadSpeed + "\", \"totalDownloadedSoFar\": \""
                                + totalDownloadedBytes + "\", \"timeElapsed\" : \""
                                + timeElapsed + "\"}";

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (tv != null) {
                                    tv.setText(downloadData);
                                    writeToFile(downloadData);
                                }
                            }
                        });
                    }
                    currentStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                    if (currentStatus != DownloadManager.STATUS_RUNNING && currentStatus != DownloadManager.STATUS_PENDING) {
                        downloadId = downloadManager.enqueue(request);
                        downloadedBytes = 0;
                    }

                    cursor.close();
                }

                String downloadData = "{\"status\":\"ERROR\"}";
                if (tv != null) {
                    tv.setText(downloadData);
                }

            }
        }).start();
    }
}