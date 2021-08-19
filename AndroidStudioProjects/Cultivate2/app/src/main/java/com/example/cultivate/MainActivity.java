package com.example.cultivate;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;

import me.drakeet.materialdialog.MaterialDialog;

public class MainActivity extends AppCompatActivity {

    private Uri uri;
    private int max;
    private static final String MODEL_PATH = "model.tflite";
    private static final String LABEL_PATH = "labels.txt";
    private final int SELECT_PHOTO = 101;
    private final int CAPTURE_PHOTO = 102;
    private final int ImageWidth= 256;
    private final int ImageHeight= 256;
    private final int DIM_BATCH_SIZE = 1;
    private final float result[]=null;
    private final int DIM_PIXEL_SIZE = 3;
    private final int IMAGE_MEAN = 128;
    private final float IMAGE_STD = 128.0f;
    private int[] intValues = new int[ImageWidth * ImageHeight];
    private ArrayList<Float> array=new ArrayList<Float>(19);
    private Interpreter interpreter;
    private List<String> labelList=null;
    private String output=null;
    private ByteBuffer imgData = null;
    private float[][] labelProbArray;
    private Button camera;
    private Button detect;
    private ImageView imageView;
    private TextView textView;
    private final int REQUEST_CODE_WRITE_STORAGE = 1;
    private Bitmap bitmap=null;
    private final int MAX_RESULTS=3;
    private final float THRESHOLD=0.0f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        camera=(Button)findViewById(R.id.photo);
        detect=(Button)findViewById(R.id.detect);
        imageView=(ImageView)findViewById(R.id.image);
        textView=(TextView)findViewById(R.id.text);
        try {
            interpreter=new Interpreter(loadModelFile());
            labelList=loadLabelList();
        } catch (IOException e) {
            e.printStackTrace();
        }

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hasWriteStoragePermission = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    hasWriteStoragePermission = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }

                if (hasWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_CODE_WRITE_STORAGE);
                    }

                }
                listDialogue();

            }
        });

        detect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int out;
                Bitmap bm=resizedBitmap();
                convertBitmapToByteBuffer(bm);
                labelProbArray=new float[1][labelList.size()];
                long startTime = SystemClock.uptimeMillis();
                interpreter.run(imgData,labelProbArray);
                long endTime = SystemClock.uptimeMillis();
               System.out.println(endTime-startTime);
                for(int j=0;j<19;j++)
                {
                    array.add(labelProbArray[0][j]);
                    System.out.print(labelProbArray[0][j]+" ");
                }
                max=array.indexOf(Collections.max(array));
                textView.setText(labelList.get(max));
            }
        });
    }

    public Bitmap resizedBitmap()
    {
        Bitmap resizedBitmap=Bitmap.createScaledBitmap(bitmap,ImageWidth,ImageHeight,true);
        return resizedBitmap;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        imgData = ByteBuffer.allocateDirect(4 * DIM_BATCH_SIZE * ImageWidth* ImageWidth * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int Pixel = 0;

        for (int i = 0; i < ImageWidth; ++i) {
            for (int j = 0; j < ImageHeight; ++j) {
                final int val = intValues[Pixel++];
                imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
    }

    public void listDialogue(){
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        arrayAdapter.add("Take Photo");
        arrayAdapter.add("Select Gallery");
        ListView listView = new ListView(this);
        listView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        float scale = getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (8 * scale + 0.5f);
        listView.setPadding(0, dpAsPixels, 0, dpAsPixels);
        listView.setDividerHeight(0);
        listView.setAdapter(arrayAdapter);
        final MaterialDialog alert = new MaterialDialog(this).setContentView(listView);
        alert.setPositiveButton("Cancel", new View.OnClickListener() {
            @Override public void onClick(View v) {
                alert.dismiss();
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(position==0){
                    alert.dismiss();
                    Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    //Uri uri  = Uri.parse("file:///sdcard/photo.jpg");
                    String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "propic.jpg";
                    Uri uri = Uri.parse(root);
                    //i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                    startActivityForResult(i, CAPTURE_PHOTO);
                }else {
                    alert.dismiss();
                    Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                }
            }
        });
        alert.show();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        switch (requestCode) {
            case SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    Uri imageUri = imageReturnedIntent.getData();
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        imageView.setImageBitmap(bitmap);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;

            case CAPTURE_PHOTO:
                if (resultCode == RESULT_OK) {
                    bitmap = imageReturnedIntent.getExtras().getParcelable("data");
                    imageView.setImageBitmap(bitmap);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }

    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    public String getPath(Uri uri) {
        if (uri == null) {
            return null;
        }
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        return uri.getPath();
    }
}
