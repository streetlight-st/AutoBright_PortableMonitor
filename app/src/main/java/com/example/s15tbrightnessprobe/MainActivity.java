package com.example.s15tbrightnessprobe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements SensorEventListener {
    private TextView displayText, resultText, sensorText, usbText, logText;
    private final StringBuilder log = new StringBuilder();
    private DisplayManager dm;
    private SensorManager sm;
    private int externalId = -1;
    private boolean sensorActive = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(24, 28, 24, 48);
            scroll.addView(root);
            setContentView(scroll);
            text(root, "외부 모니터 USB 진단 0.4", 24);
            text(root, "Android 17 외부 모니터 테스트. 정차 중에만 사용하세요.", 15);
            button(root, "① 외부 디스플레이 검색", this::scan);
            displayText = text(root, "검색 전", 15);
            button(root, "② 시스템 설정 수정 권한 허용", this::requestSettings);
            button(root, "③ 모니터 밝기 100%", () -> brightness(100));
            button(root, "모니터 밝기 20%", () -> brightness(20));
            button(root, "모니터 밝기 50%", () -> brightness(50));
            resultText = text(root, "밝기 테스트 전", 15);
            button(root, "④ 조도센서 시작", this::startSensor);
            sensorText = text(root, "조도센서 측정 전", 15);
            button(root, "⑤ USB 장치 및 제어 인터페이스 조사", this::inspectUsb);
            usbText = text(root, "USB 조사 전 (DDC/CI 영상 통신과는 별도)", 13);
            button(root, "⑥ 진단 로그 복사", this::copyLog);
            logText = text(root, "", 12);
            record("앱 화면 실행 성공 / Android " + Build.VERSION.RELEASE + " SDK " + Build.VERSION.SDK_INT + " / " + Build.MODEL);
        } catch (Throwable e) {
            TextView fail = new TextView(this);
            fail.setText("초기화 오류: " + e);
            setContentView(fail);
        }
    }
    private TextView text(LinearLayout root, String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setPadding(2, 12, 2, 12);
        root.addView(t, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }
    private void button(LinearLayout root, String label, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> {
            try { action.run(); }
            catch (Throwable e) {
                record(label + ": " + android.util.Log.getStackTraceString(e));
                if (resultText != null) resultText.setText("오류: " + e + " (로그 복사)");
            }
        });
        root.addView(b, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void record(String msg) {
        log.append(msg).append('\n');
        if (logText != null) logText.setText(log.toString());
    }
    private void scan() {
        dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) { displayText.setText("DisplayManager 없음"); return; }
        externalId = -1;
        StringBuilder s = new StringBuilder();
        for (Display d : dm.getDisplays()) {
            boolean isDefault = d.getDisplayId() == Display.DEFAULT_DISPLAY;
            s.append("ID ").append(d.getDisplayId()).append(" / ").append(d.getName())
                .append(isDefault ? " [폰]" : " [외부]").append('\n');
            if (!isDefault && externalId == -1) externalId = d.getDisplayId();
        }
        s.append(externalId < 0 ? "외부 디스플레이 없음" : "선택한 외부 ID: " + externalId);
        displayText.setText(s.toString());
        record("검색: " + s.toString().replace('\n',' '));
    }
    private void requestSettings() {
        if (Settings.System.canWrite(this)) {
            record("WRITE_SETTINGS 권한 이미 허용");
            Toast.makeText(this, "권한 이미 허용됨", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        i.setData(Uri.parse("package:" + getPackageName()));
        startActivity(i);
        record("WRITE_SETTINGS 권한 화면 요청");
    }
    private void brightness(int percent) {
        if (externalId < 0 || dm == null) { resultText.setText("① 외부 화면을 먼저 검색하세요"); return; }
        if (!Settings.System.canWrite(this)) { resultText.setText("② 설정 수정 권한을 먼저 허용하세요"); return; }
        dm.setBrightness(externalId, (float)percent, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
        float readBack = dm.getBrightness(externalId, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
        resultText.setText("보낸 값 " + percent + "% / 읽힌 값 " + readBack + "%\n실제 모니터 밝기 변화는 눈으로 확인");
        record("제어: ID=" + externalId + " 입력=" + percent + "% 읽기=" + readBack + "%");
    }
    private void startSensor() {
        sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) { sensorText.setText("센서 서비스 없음"); return; }
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (sensor == null) { sensorText.setText("조도센서 사용 불가"); return; }
        if (sensorActive) sm.unregisterListener(this);
        sensorActive = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorText.setText(sensorActive ? "조도센서 시작" : "센서 등록 실패");
        record("조도센서 등록: " + sensorActive);
    }
    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_LIGHT && e.values.length > 0 && sensorText != null)
            sensorText.setText("현재 조도: " + e.values[0] + " lux");
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onPause() {
        if (sm != null) { sm.unregisterListener(this); sensorActive = false; }
        super.onPause();
    }
    /**
     * Read-only discovery only: enumerating USB class devices does not access
     * DisplayPort AUX. A TYPEC display may be visible to DisplayManager even
     * when no USB device appears here. Never send vendor commands blindly.
     */
    private void inspectUsb() {
        UsbManager manager = (UsbManager)getSystemService(Context.USB_SERVICE);
        StringBuilder out = new StringBuilder();
        if (manager == null) {
            out.append("USB Host API unavailable");
        } else {
            HashMap<String, UsbDevice> devices = manager.getDeviceList();
            out.append("USB 장치 ").append(devices.size()).append("개\n");
            for (UsbDevice d : devices.values()) {
                out.append(String.format(Locale.US, "VID:%04X PID:%04X", d.getVendorId(), d.getProductId()));
                out.append(" 클래스:").append(d.getDeviceClass());
                out.append(" 인터페이스:").append(d.getInterfaceCount()).append("\n");
                for (int i = 0; i < d.getInterfaceCount(); i++) {
                    UsbInterface inf = d.getInterface(i);
                    out.append("  #").append(i)
                       .append(" class=").append(inf.getInterfaceClass())
                       .append(" subclass=").append(inf.getInterfaceSubclass())
                       .append(" protocol=").append(inf.getInterfaceProtocol())
                       .append(" endpoints=").append(inf.getEndpointCount()).append("\n");
                    for (int e = 0; e < inf.getEndpointCount(); e++) {
                        UsbEndpoint ep = inf.getEndpoint(e);
                        out.append("    ep=0x").append(Integer.toHexString(ep.getAddress()))
                           .append(" type=").append(ep.getType()).append("\n");
                    }
                }
            }
        }
        File onigiriNode = new File("/sys/module/msm_drm/parameters/secdp_ddc_brightness");
        out.append("Onigiri DDC 커널 노드 존재: ").append(onigiriNode.exists()).append("\n");
        out.append("주의: DP AUX 영상 경로는 USB 호스트 장치 목록에 보이지 않을 수 있음.");
        String report = out.toString();
        usbText.setText(report);
        record("USB 조사(읽기 전용): " + report.replace('\n', ' '));
    }
    private void copyLog() {
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("S15T diagnostics", log.toString()));
            Toast.makeText(this, "로그 복사 완료", Toast.LENGTH_SHORT).show();
        }
    }
}
