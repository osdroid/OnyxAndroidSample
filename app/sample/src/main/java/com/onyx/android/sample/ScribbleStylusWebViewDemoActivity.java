package com.onyx.android.sample;

import android.content.Context;
import android.graphics.Matrix;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.onyx.android.sample.device.DeviceConfig;
import com.onyx.android.sdk.api.device.epd.EpdController;
import com.onyx.android.sdk.api.device.epd.UpdateMode;
import com.onyx.android.sdk.common.request.WakeLockHolder;
import com.onyx.android.sdk.scribble.api.PenReader;
import com.onyx.android.sdk.scribble.data.TouchPoint;
import com.onyx.android.sdk.scribble.data.TouchPointList;
import com.onyx.android.sdk.utils.DeviceUtils;

import java.io.IOException;
import java.io.InputStream;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ScribbleStylusWebViewDemoActivity extends AppCompatActivity implements View.OnClickListener {

    @Bind(R.id.button_pen)
    Button buttonPen;
    @Bind(R.id.button_eraser)
    Button buttonEraser;
    @Bind(R.id.surfaceview)
    WebView webView;

    boolean scribbleMode = false;
    private PenReader penReader;
    private Matrix viewMatrix;

    private WakeLockHolder wakeLockHolder = new WakeLockHolder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scribble_webview_stylus_demo);

        ButterKnife.bind(this);
        buttonPen.setOnClickListener(this);
        buttonEraser.setOnClickListener(this);

        initWebView();
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String js = "android.btns(getBtns());";
            webView.loadUrl("javascript:" + js);
        }
    }

    public class WebJsInterface {
        Context mContext;

        WebJsInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public void testJsCallback() {
            leaveScribbleMode();
            getWindow().getDecorView().postInvalidate();
            Toast.makeText(mContext, "Quit scribble from WebView", Toast.LENGTH_SHORT).show();
        }

    }

    private String readHtmlFile() {
        InputStream in = getResources().openRawResource(R.raw.demo);
        StringBuilder builder = new StringBuilder();
        try {
            int count;
            byte[] bytes = new byte[32768];
            while ( (count = in.read(bytes,0, 32768)) > 0) {
                builder.append(new String(bytes, 0, count));
            }

            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    private void initWebView() {
        webView.setWebViewClient(new MyWebViewClient());
        webView.addJavascriptInterface(new WebJsInterface(this), "android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadData(readHtmlFile(), "text/html", "utf-8");
        webView.post(new Runnable() {
            @Override
            public void run() {
                initPenReader();
                updateViewMatrix();
            }
        });

    }

    public PenReader getPenReader() {
        if (penReader == null) {
            penReader = new PenReader(this, webView);
        }
        return penReader;
    }

    private void updateViewMatrix() {
        int viewPosition[] = {0, 0};
        webView.getLocationOnScreen(viewPosition);
        viewMatrix = new Matrix();
        viewMatrix.postTranslate(-viewPosition[0], -viewPosition[1]);
    }

    private void initPenReader() {
        getPenReader().setPenReaderCallback(new PenReader.PenReaderCallback() {
            final float baseWidth = 5;
            final float pressure = 1;
            final float size = 1;
            boolean begin = false;
            @Override
            public void onBeginRawData() {
                begin = true;
                enterScribbleMode();
            }

            @Override
            public void onEndRawData() {

            }

            @Override
            public void onRawTouchPointMoveReceived(TouchPoint touchPoint) {
                if (begin) {
                    EpdController.moveTo(webView, touchPoint.x, touchPoint.y, baseWidth);
                } else {
                    EpdController.quadTo(webView, touchPoint.x, touchPoint.y, UpdateMode.DU);
                }
                begin = false;
            }

            @Override
            public void onRawTouchPointListReceived(TouchPointList touchPointList) {
            }

            @Override
            public void onBeginErasing() {

            }

            @Override
            public void onEndErasing() {

            }

            @Override
            public void onEraseTouchPointMoveReceived(TouchPoint touchPoint) {

            }

            @Override
            public void onEraseTouchPointListReceived(TouchPointList touchPointList) {

            }

        });
    }

    @Override
    protected void onDestroy() {
        leaveScribbleMode();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        getPenReader().pause();
        super.onPause();
    }

    @Override
    public void onClick(View v) {
        if (v.equals(buttonPen)) {
            penStart();
            return;
        } else if (v.equals(buttonEraser)) {
            leaveScribbleMode();
            return;
        }
    }

    private void enterScribbleMode() {
        EpdController.enterScribbleMode(webView);
        scribbleMode = true;
    }

    private void penStart() {
        wakeLockHolder.acquireWakeLock(this, "scribble");

        getPenReader().start();
        getPenReader().resume();
    }

    private void leaveScribbleMode() {
        scribbleMode = false;
        EpdController.leaveScribbleMode(webView);
        getPenReader().stop();

        wakeLockHolder.forceReleaseWakeLock();
    }

    @Override
    protected void onResume() {
        getPenReader().resume();
        super.onResume();
    }

}
