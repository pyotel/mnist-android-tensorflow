package mariannelinhares.mnistandroid;

/*
   Copyright 2016 Narrative Nights Inc. All Rights Reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   From: https://raw.githubusercontent.com/miyosuda/TensorFlowAndroidMNIST/master/app/src/main/java/jp/narr/tensorflowmnist/DrawModel.java
*/

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mariannelinhares.mnistandroid.views.DrawModel;
import mariannelinhares.mnistandroid.views.DrawView;

/**
 * Changed by marianne-linhares on 21/04/17.
 * https://raw.githubusercontent.com/miyosuda/TensorFlowAndroidMNIST/master/app/src/main/java/jp/narr/tensorflowmnist/DrawModel.java
 */

public class MainActivity extends Activity implements View.OnClickListener, View.OnTouchListener {

    // ui related
    private Button clearBtn, classBtn;
    private TextView resText;

    // tensorflow input and output
    private static final int INPUT_SIZE = 28;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    private static final String MODEL_FILE = "file:///android_asset/expert-graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/labels.txt";

    private Classifier classifier;

    private Executor executor = Executors.newSingleThreadExecutor();

    // views related
    private DrawModel drawModel;
    private DrawView drawView;
    private static final int PIXEL_WIDTH = 28;

    private PointF mTmpPiont = new PointF();

    private float mLastX;
    private float mLastY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get drawing view
        drawView = (DrawView)findViewById(R.id.draw);
        drawModel = new DrawModel(PIXEL_WIDTH, PIXEL_WIDTH);

        drawView.setModel(drawModel);
        drawView.setOnTouchListener(this);

        //clear button
        clearBtn = (Button)findViewById(R.id.btn_clear);
        clearBtn.setOnClickListener(this);

        //class button
        classBtn = (Button)findViewById(R.id.btn_class);
        classBtn.setOnClickListener(this);

        //benchmark button
        classBtn = (Button)findViewById(R.id.btn_benchmark);
        classBtn.setOnClickListener(this);

        // res text
        resText = (TextView)findViewById(R.id.tfRes);

        // tensorflow
        loadModel();
    }

    private void loadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = Classifier.create(getApplicationContext().getAssets(),
                                                   MODEL_FILE,
                                                   LABEL_FILE,
                                                   INPUT_SIZE,
                                                   INPUT_NAME,
                                                   OUTPUT_NAME);
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }


    @Override
    public void onClick(View view){
        int dummy = view.getId();
        if(view.getId() == R.id.btn_clear) {
            drawModel.clear();
            drawView.reset();
            drawView.invalidate();

            resText.setText("Result: ");
        }
        else if(view.getId() == R.id.btn_class){

            float pixels[] = drawView.getPixelData();

            final Classification res = classifier.recognize(pixels);
            String result = "Result: ";
            if (res.getLabel() == null) {
                resText.setText(result + "?");
            }
            else {
                result += res.getLabel();
                result += "\nwith probability: " + res.getConf();
                resText.setText(result);
            }
        }
        else if(view.getId() == R.id.btn_benchmark){
            long total_time = 0;
            int image_cnt = 0;
            int image_max = 1;
            try {
                ////////////////////////////////////////////////////////////////
                AssetManager assetManager = getAssets();
                InputStream ims = assetManager.open("output.txt");
                InputStreamReader r = new InputStreamReader(ims);
                BufferedReader in = new BufferedReader(r);

                String str;
                String strarr[];
                while ((str = in.readLine()) != null) {
                    strarr = str.split(" ");
                    float pixels[] = new float[strarr.length];
                    image_cnt++;
                    for(int i=0 ; i < strarr.length ; i ++){
                        pixels[i] = Float.parseFloat(strarr[i]);
                    }
                    long start = System.currentTimeMillis();
                    final Classification res = classifier.recognize(pixels);
                    total_time += ( System.currentTimeMillis() - start );
                    String result = "cur image cnt : " + image_cnt;
                    resText.setText(result);
                    if(image_cnt == image_max && image_max != -1) {
                        break;
                    }
                }
                in.close();
                ////////////////////////////////////////////////////////////////
            } catch (IOException e) {
                System.err.println(e); // 에러가 있다면 메시지 출력
            }

            String result = "Result: ";
            result += "\nbenchmark total time(ms): " + total_time;
            result += "\ntotal test images count : " + image_cnt;
            resText.setText(result);

        }
    }

    @Override
    protected void onResume() {
        drawView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        drawView.onPause();
        super.onPause();
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if (action == MotionEvent.ACTION_DOWN) {
            processTouchDown(event);
            return true;

        } else if (action == MotionEvent.ACTION_MOVE) {
            processTouchMove(event);
            return true;

        } else if (action == MotionEvent.ACTION_UP) {
            processTouchUp();
            return true;
        }
        return false;
    }

    private void processTouchDown(MotionEvent event) {
        mLastX = event.getX();
        mLastY = event.getY();
        drawView.calcPos(mLastX, mLastY, mTmpPiont);
        float lastConvX = mTmpPiont.x;
        float lastConvY = mTmpPiont.y;
        drawModel.startLine(lastConvX, lastConvY);
    }

    private void processTouchMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        drawView.calcPos(x, y, mTmpPiont);
        float newConvX = mTmpPiont.x;
        float newConvY = mTmpPiont.y;
        drawModel.addLineElem(newConvX, newConvY);

        mLastX = x;
        mLastY = y;
        drawView.invalidate();
    }

    private void processTouchUp() {
        drawModel.endLine();
    }
}