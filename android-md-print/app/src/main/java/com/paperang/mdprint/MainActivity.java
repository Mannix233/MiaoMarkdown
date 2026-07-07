package com.paperang.mdprint;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;

public class MainActivity extends Activity {
    private static final UUID PAPERANG_SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455");
    private static final UUID PAPERANG_WRITE_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3");
    private static final UUID PAPERANG_NOTIFY_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private static final int WIDTH_PX = 384;
    private static final int WIDTH_BYTES = 48;
    private static final int PAPER_PADDING_PX = 22;
    private static final float DEFAULT_CONTENT_TEXT_PX = 19f;
    private static final float ADVANCE_MM_PER_PX = 0.1217f;
    private static final int MIN_PRINT_HEIGHT_PX = 165;
    private static final int MAX_PRINT_HEIGHT_PX = 12000;
    private static final int STANDARD_CRC_KEY = 0x35769521;
    private static final int SESSION_CRC_KEY = 0x06968634 ^ 0x002e696d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> bleWriteQueue = new ArrayDeque<>();
    private final ExecutorService classicWriter = Executors.newSingleThreadExecutor();
    private boolean bleWriting = false;
    private int crcKey = STANDARD_CRC_KEY;

    private EditText editor;
    private TextView preview;
    private TextView connectionStatus;
    private TextView routeStatus;
    private TextView estimateStatus;
    private TextView fontStatus;
    private TextView status;
    private ScrollView editorPanel;
    private ScrollView previewPanel;
    private HorizontalScrollView previewScroller;
    private Button editTab;
    private Button previewTab;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic bleWriteCharacteristic;
    private BluetoothLeScanner scanner;
    private BluetoothSocket classicSocket;
    private OutputStream classicOutput;
    private Markwon markwon;
    private float contentTextPx = DEFAULT_CONTENT_TEXT_PX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNeededPermissions();
    }

    @Override
    protected void onDestroy() {
        closeConnections();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(Color.rgb(234, 239, 235));

        LinearLayout header = panel(Color.rgb(22, 31, 29), 0);
        header.setPadding(20, 18, 20, 18);
        TextView title = label("喵喵 Markdown 打印", 24, Color.WHITE, true);
        TextView subtitle = label("Paperang P1 · 384px 点阵纸带预览", 13, Color.rgb(180, 197, 190), false);
        header.addView(title);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, 12, 0, 10);
        connectionStatus = chip("未连接", Color.rgb(255, 255, 255), Color.rgb(31, 44, 41));
        routeStatus = chip("通道：无", Color.rgb(213, 231, 223), Color.rgb(31, 44, 41));
        statusRow.addView(connectionStatus, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams routeParams = new LinearLayout.LayoutParams(0, -2, 1);
        routeParams.setMargins(10, 0, 0, 0);
        statusRow.addView(routeStatus, routeParams);
        root.addView(statusRow);

        estimateStatus = chip("纸宽 384px · 高度待计算 · 0.1217mm/px", Color.rgb(255, 251, 235), Color.rgb(62, 49, 25));
        root.addView(estimateStatus, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setPadding(0, 8, 0, 0);
        Button smaller = actionButton("字号 -");
        smaller.setOnClickListener(v -> adjustTextSize(-1f));
        fontStatus = chip("字号 19px", Color.rgb(255, 255, 255), Color.rgb(31, 44, 41));
        Button larger = actionButton("字号 +");
        larger.setOnClickListener(v -> adjustTextSize(1f));
        sizeRow.addView(smaller, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams fontParams = new LinearLayout.LayoutParams(0, -2, 1);
        fontParams.setMargins(8, 0, 0, 0);
        sizeRow.addView(fontStatus, fontParams);
        addRowButton(sizeRow, larger);
        root.addView(sizeRow);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        editTab = actionButton("编辑");
        previewTab = actionButton("预览");
        editTab.setOnClickListener(v -> showEditor());
        previewTab.setOnClickListener(v -> showPreview());
        tabs.addView(editTab, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams previewTabParams = new LinearLayout.LayoutParams(0, -2, 1);
        previewTabParams.setMargins(8, 0, 0, 0);
        tabs.addView(previewTab, previewTabParams);
        root.addView(tabs);

        editor = new EditText(this);
        editor.setTextSize(16);
        editor.setTextColor(Color.rgb(22, 31, 29));
        editor.setBackgroundColor(Color.WHITE);
        editor.setPadding(18, 18, 18, 18);
        editor.setGravity(android.view.Gravity.TOP);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setText("# Print test\n\nItem | Meaning | Action\n--- | --- | ---\nF(s) | Input transform | Inverse Laplace\n\n- Short lines first\n- Markdown marks should disappear\n\n**Bold text** and `code`.");
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (previewScroller != null && previewScroller.getVisibility() == View.VISIBLE) {
                    updatePreview();
                } else if (estimateStatus != null) {
                    updateEstimate();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        editorPanel = new ScrollView(this);
        editorPanel.setFillViewport(true);
        editorPanel.setBackground(cardBackground(Color.WHITE, Color.rgb(204, 214, 209), 8));
        editorPanel.addView(editor, new ScrollView.LayoutParams(-1, -2));

        preview = createPaperView();
        previewPanel = new ScrollView(this);
        previewPanel.setFillViewport(true);
        previewPanel.setBackgroundColor(Color.rgb(224, 230, 226));
        previewPanel.setPadding(18, 18, 18, 28);
        previewPanel.addView(preview, new ScrollView.LayoutParams(WIDTH_PX, -2));
        previewScroller = new HorizontalScrollView(this);
        previewScroller.setFillViewport(false);
        previewScroller.setBackground(cardBackground(Color.rgb(224, 230, 226), Color.rgb(184, 196, 190), 8));
        previewScroller.addView(previewPanel, new HorizontalScrollView.LayoutParams(-2, -1));
        previewScroller.setVisibility(View.GONE);

        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.setMargins(0, 10, 0, 10);
        root.addView(editorPanel, editorParams);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1);
        previewParams.setMargins(0, 10, 0, 10);
        root.addView(previewScroller, previewParams);

        LinearLayout sampleRow = new LinearLayout(this);
        sampleRow.setOrientation(LinearLayout.HORIZONTAL);
        Button sample = actionButton("示例");
        sample.setOnClickListener(v -> editor.setText("# Paperang P1\n\n- 原生安卓打印\n- Markdown 预览与纸上输出统一\n\n项目 | 结果\n--- | ---\n黑条 | OK\n文字 | OK\n\n`paperang` 应该是一行内正常长度。"));
        Button classicConnect = actionButton("经典蓝牙");
        classicConnect.setOnClickListener(v -> connectFirstPairedClassicDevice());
        Button bleConnect = actionButton("BLE");
        bleConnect.setOnClickListener(v -> startBleScan());
        sampleRow.addView(sample, new LinearLayout.LayoutParams(0, -2, 1));
        addRowButton(sampleRow, classicConnect);
        addRowButton(sampleRow, bleConnect);
        root.addView(sampleRow);

        LinearLayout printRow = new LinearLayout(this);
        printRow.setOrientation(LinearLayout.HORIZONTAL);
        Button stripe = primaryButton("黑条测试");
        stripe.setOnClickListener(v -> printBlackStripe());
        Button print = primaryButton("打印 Markdown");
        print.setOnClickListener(v -> printMarkdown());
        printRow.addView(stripe, new LinearLayout.LayoutParams(0, -2, 1));
        addRowButton(printRow, print);
        root.addView(printRow);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(Color.rgb(45, 57, 54));
        status.setPadding(14, 12, 14, 12);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(cardBackground(Color.rgb(247, 249, 247), Color.rgb(204, 214, 209), 8));
        scroll.addView(status);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(-1, 150);
        logParams.setMargins(0, 10, 0, 0);
        root.addView(scroll, logParams);

        setContentView(root);
        showEditor();
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private TextView chip(String text, int background, int foreground) {
        TextView view = label(text, 13, foreground, true);
        view.setPadding(14, 10, 14, 10);
        view.setBackground(cardBackground(background, Color.rgb(204, 214, 209), 8));
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(23, 34, 31));
        button.setBackground(cardBackground(Color.rgb(246, 248, 246), Color.rgb(177, 191, 184), 8));
        button.setPadding(8, 8, 8, 8);
        return button;
    }

    private Button primaryButton(String text) {
        Button button = actionButton(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(cardBackground(Color.rgb(29, 88, 74), Color.rgb(29, 88, 74), 8));
        return button;
    }

    private LinearLayout panel(int background, int stroke) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(cardBackground(background, stroke, 8));
        return view;
    }

    private GradientDrawable cardBackground(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != 0) {
            drawable.setStroke(1, stroke);
        }
        return drawable;
    }

    private void addRowButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(8, 0, 0, 0);
        row.addView(button, params);
    }

    private void showEditor() {
        editorPanel.setVisibility(View.VISIBLE);
        previewScroller.setVisibility(View.GONE);
        editTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        previewTab.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        editTab.setBackground(cardBackground(Color.rgb(29, 88, 74), Color.rgb(29, 88, 74), 8));
        editTab.setTextColor(Color.WHITE);
        previewTab.setBackground(cardBackground(Color.rgb(246, 248, 246), Color.rgb(177, 191, 184), 8));
        previewTab.setTextColor(Color.rgb(23, 34, 31));
        updateEstimate();
    }

    private void showPreview() {
        updatePreview();
        editorPanel.setVisibility(View.GONE);
        previewScroller.setVisibility(View.VISIBLE);
        editTab.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        previewTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        editTab.setBackground(cardBackground(Color.rgb(246, 248, 246), Color.rgb(177, 191, 184), 8));
        editTab.setTextColor(Color.rgb(23, 34, 31));
        previewTab.setBackground(cardBackground(Color.rgb(29, 88, 74), Color.rgb(29, 88, 74), 8));
        previewTab.setTextColor(Color.WHITE);
    }

    private void updatePreview() {
        markdownRenderer().setMarkdown(preview, editor.getText().toString());
        updateEstimate(measurePaperView(preview));
    }

    private void adjustTextSize(float deltaPx) {
        contentTextPx = Math.max(12f, Math.min(28f, contentTextPx + deltaPx));
        if (fontStatus != null) {
            fontStatus.setText("字号 " + Math.round(contentTextPx) + "px");
        }
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTextPx);
        if (previewScroller.getVisibility() == View.VISIBLE) {
            updatePreview();
        } else {
            updateEstimate();
        }
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 10);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        }
    }

    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private void startBleScan() {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log("蓝牙未开启。");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            log("没有可用的 BLE 扫描器。");
            return;
        }
        setDeviceStatus("正在扫描 BLE", "通道：BLE 8841");
        log("正在扫描 BLE...");
        try {
            scanner.startScan(scanCallback);
            handler.postDelayed(() -> {
                try {
                    scanner.stopScan(scanCallback);
                } catch (SecurityException ignored) {
                }
            }, 12000);
        } catch (SecurityException e) {
            log("BLE 扫描权限被拒绝。");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = safeDeviceName(device);
            if (isPaperangName(name)) {
                try {
                    scanner.stopScan(this);
                } catch (SecurityException ignored) {
                }
                log("找到 BLE 设备：" + name + " " + safeAddress(device));
                setDeviceStatus("找到：" + safeLabel(name), "通道：BLE 8841");
                connectBleDevice(device);
            }
        }
    };

    private void connectBleDevice(BluetoothDevice device) {
        closeClassic();
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
        log("正在连接 BLE...");
        setDeviceStatus("BLE 连接中", "通道：BLE 8841");
        try {
            gatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            log("BLE 连接权限被拒绝。");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BLE 已连接，正在发现服务...");
                setDeviceStatus("BLE 已连接", "通道：BLE 8841");
                try {
                    g.discoverServices();
                } catch (SecurityException e) {
                    log("BLE 服务发现权限被拒绝。");
                }
            } else {
                log("BLE 已断开：" + statusCode);
                setDeviceStatus("BLE 已断开", "通道：无");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            BluetoothGattService service = g.getService(PAPERANG_SERVICE_UUID);
            if (service == null) {
                log("没有找到 Paperang BLE 服务。");
                return;
            }
            bleWriteCharacteristic = service.getCharacteristic(PAPERANG_WRITE_UUID);
            if (bleWriteCharacteristic == null) {
                log("没有找到 BLE 写入特征。");
                return;
            }
            setDeviceStatus("BLE 就绪", "写入：8841");
            BluetoothGattCharacteristic notify = service.getCharacteristic(PAPERANG_NOTIFY_UUID);
            if (notify != null) {
                try {
                    g.setCharacteristicNotification(notify, true);
                    BluetoothGattDescriptor descriptor = notify.getDescriptor(CCCD_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        g.writeDescriptor(descriptor);
                    }
                } catch (SecurityException e) {
                    log("BLE 通知权限被拒绝。");
                }
            }
            handler.postDelayed(MainActivity.this::initializePrinter, 500);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            bleWriting = false;
            handler.postDelayed(MainActivity.this::drainBleQueue, 80);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] value = c.getValue();
            int command = value.length > 1 ? value[1] & 0xff : -1;
            log("BLE 通知 cmd=" + command);
        }
    };

    private void connectFirstPairedClassicDevice() {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log("蓝牙未开启。");
            return;
        }
        new Thread(() -> {
            try {
                Set<BluetoothDevice> paired = adapter.getBondedDevices();
                BluetoothDevice target = null;
                for (BluetoothDevice device : paired) {
                    if (isPaperangName(safeDeviceName(device))) {
                        target = device;
                        break;
                    }
                }
                if (target == null) {
                    log("没有已配对的 Paperang/喵喵机，请先在系统蓝牙里配对。");
                    setDeviceStatus("未找到已配对打印机", "通道：无");
                    return;
                }
                log("正在连接经典蓝牙：" + safeDeviceName(target) + " " + safeAddress(target));
                setDeviceStatus("经典蓝牙连接中", "通道：SPP");
                closeBle();
                closeClassic();
                classicSocket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                adapter.cancelDiscovery();
                classicSocket.connect();
                classicOutput = classicSocket.getOutputStream();
                log("经典蓝牙已连接。");
                setDeviceStatus("经典蓝牙就绪：" + safeLabel(safeDeviceName(target)), "通道：SPP");
                initializePrinter();
            } catch (SecurityException e) {
                log("经典蓝牙权限被拒绝。");
            } catch (IOException e) {
                log("经典蓝牙连接失败：" + e.getMessage());
                setDeviceStatus("经典蓝牙失败", "通道：无");
                closeClassic();
            }
        }).start();
    }

    private void initializePrinter() {
        crcKey = STANDARD_CRC_KEY;
        sendRaw(pack(24, int32le(SESSION_CRC_KEY ^ STANDARD_CRC_KEY), 0));
        crcKey = SESSION_CRC_KEY;
        sendRaw(pack(34, new byte[]{0}, 0));
        sendRaw(pack(44, new byte[]{0}, 0));
        sendRaw(pack(25, new byte[]{75}, 0));
        log("打印机初始化完成。");
    }

    private void printBlackStripe() {
        if (!ready()) return;
        byte[] data = new byte[WIDTH_BYTES * 32];
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < WIDTH_BYTES; x++) {
                data[y * WIDTH_BYTES + x] = (byte) 0xff;
            }
        }
        sendImage(data);
    }

    private void printMarkdown() {
        if (!ready()) return;
        RenderedPaper paper = renderMarkdown(editor.getText().toString());
        updateEstimate(paper.heightPx);
        log("打印预估：宽 384px，高 " + paper.heightPx + "px，约 " + formatMm(estimateLengthMm(paper.heightPx)) + "mm");
        sendImage(paper.bytes);
    }

    private void sendImage(byte[] image) {
        sendRaw(pack(34, new byte[]{0}, 0));
        sendRaw(pack(44, new byte[]{0}, 0));
        int packetId = 0;
        for (int offset = 0; offset < image.length; offset += WIDTH_BYTES) {
            int len = Math.min(WIDTH_BYTES, image.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(image, offset, chunk, 0, len);
            sendRaw(pack(0, chunk, packetId++));
        }
        sendRaw(pack(44, new byte[]{0}, 0));
        sendRaw(pack(26, int16le(280), 0));
        int heightPx = image.length / WIDTH_BYTES;
        log("已入队：宽 384px，高 " + heightPx + "px，数据 " + image.length + " bytes，预计 " + formatMm(estimateLengthMm(heightPx)) + "mm");
    }

    private RenderedPaper renderMarkdown(String markdown) {
        TextView paper = createPaperView();
        markdownRenderer().setMarkdown(paper, markdown);
        int height = measurePaperView(paper);
        if (height > MAX_PRINT_HEIGHT_PX) {
            height = MAX_PRINT_HEIGHT_PX;
            log("内容超过最大渲染高度，已截到 " + MAX_PRINT_HEIGHT_PX + "px。");
        }

        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        paper.draw(canvas);
        return new RenderedPaper(encodeBitmap(bitmap), height);
    }

    private TextView createPaperView() {
        TextView paper = new TextView(this);
        paper.setTextColor(Color.BLACK);
        paper.setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTextPx);
        paper.setIncludeFontPadding(true);
        paper.setPadding(PAPER_PADDING_PX, PAPER_PADDING_PX, PAPER_PADDING_PX, PAPER_PADDING_PX);
        paper.setLineSpacing(0, 1.05f);
        paper.setBackgroundColor(Color.WHITE);
        paper.setMinWidth(WIDTH_PX);
        paper.setMaxWidth(WIDTH_PX);
        paper.setWidth(WIDTH_PX);
        return paper;
    }

    private int measurePaperView(TextView paper) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        paper.measure(widthSpec, heightSpec);
        int height = Math.max(MIN_PRINT_HEIGHT_PX, paper.getMeasuredHeight());
        paper.layout(0, 0, WIDTH_PX, height);
        return height;
    }

    private void updateEstimate() {
        TextView paper = createPaperView();
        markdownRenderer().setMarkdown(paper, editor.getText().toString());
        updateEstimate(measurePaperView(paper));
    }

    private void updateEstimate(int heightPx) {
        if (estimateStatus != null) {
            int dotRows = Math.max(1, heightPx);
            estimateStatus.setText("384px · 高 " + heightPx + "px / " + dotRows + "行 · 约 " + formatMm(estimateLengthMm(heightPx)) + "mm · 0.1217mm/px");
        }
    }

    private float estimateLengthMm(int heightPx) {
        return Math.max(20f, heightPx * ADVANCE_MM_PER_PX);
    }

    private String formatMm(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static class RenderedPaper {
        final byte[] bytes;
        final int heightPx;

        RenderedPaper(byte[] bytes, int heightPx) {
            this.bytes = bytes;
            this.heightPx = heightPx;
        }
    }

    private Markwon markdownRenderer() {
        if (markwon == null) {
            markwon = Markwon.builder(this)
                    .usePlugin(TablePlugin.create(this))
                    .usePlugin(TaskListPlugin.create(this))
                    .usePlugin(StrikethroughPlugin.create())
                    .usePlugin(HtmlPlugin.create())
                    .build();
        }
        return markwon;
    }

    private byte[] encodeBitmap(Bitmap bitmap) {
        byte[] out = new byte[bitmap.getHeight() * WIDTH_BYTES];
        int index = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int xb = 0; xb < WIDTH_BYTES; xb++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xb * 8 + bit;
                    int pixel = bitmap.getPixel(x, y);
                    int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                    value = (value << 1) | (gray < 128 ? 1 : 0);
                }
                out[index++] = (byte) value;
            }
        }
        return out;
    }

    private byte[] pack(int command, byte[] data, int packetId) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 1 + 2 + data.length + 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 2);
        buffer.put((byte) command);
        buffer.put((byte) packetId);
        buffer.putShort((short) data.length);
        buffer.put(data);
        buffer.putInt(crc32(data));
        buffer.put((byte) 3);
        return buffer.array();
    }

    private int crc32(byte[] data) {
        int crc = crcKey ^ 0xffffffff;
        for (byte b : data) {
            crc = CRC_TABLE[(crc ^ b) & 0xff] ^ (crc >>> 8);
        }
        return crc ^ 0xffffffff;
    }

    private static final int[] CRC_TABLE = makeCrcTable();

    private static int[] makeCrcTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            }
            table[i] = c;
        }
        return table;
    }

    private byte[] int16le(int value) {
        return new byte[]{(byte) (value & 0xff), (byte) ((value >>> 8) & 0xff)};
    }

    private byte[] int32le(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >>> 8) & 0xff),
                (byte) ((value >>> 16) & 0xff),
                (byte) ((value >>> 24) & 0xff)
        };
    }

    private void sendRaw(byte[] packet) {
        OutputStream output = classicOutput;
        if (output != null) {
            classicWriter.execute(() -> {
                try {
                    output.write(packet);
                    output.flush();
                    Thread.sleep(45);
                } catch (IOException e) {
                    log("Classic write failed: " + e.getMessage());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            return;
        }
        bleWriteQueue.add(packet);
        drainBleQueue();
    }

    private void drainBleQueue() {
        if (bleWriting || bleWriteQueue.isEmpty() || bleWriteCharacteristic == null || gatt == null) return;
        byte[] packet = bleWriteQueue.poll();
        try {
            bleWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            bleWriteCharacteristic.setValue(packet);
            bleWriting = true;
            boolean ok = gatt.writeCharacteristic(bleWriteCharacteristic);
            if (!ok) {
                bleWriting = false;
                log("BLE writeCharacteristic returned false");
            }
        } catch (SecurityException e) {
            bleWriting = false;
            log("BLE write permission denied.");
        }
    }

    private boolean ready() {
        if (classicOutput != null || bleWriteCharacteristic != null) {
            return true;
        }
        log("请先连接经典蓝牙或 BLE。");
        return false;
    }

    private boolean isPaperangName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.contains("paperang") || lower.contains("miao");
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "" : name;
        } catch (SecurityException e) {
            return "";
        }
    }

    private String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException e) {
            return "";
        }
    }

    private String safeLabel(String value) {
        return value == null || value.length() == 0 ? "Paperang" : value;
    }

    private void setDeviceStatus(String connection, String route) {
        handler.post(() -> {
            if (connectionStatus != null) {
                connectionStatus.setText(connection);
            }
            if (routeStatus != null) {
                routeStatus.setText(route);
            }
        });
    }

    private void closeConnections() {
        closeBle();
        closeClassic();
        classicWriter.shutdownNow();
    }

    private void closeBle() {
        if (gatt != null) {
            try {
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
            bleWriteCharacteristic = null;
            bleWriteQueue.clear();
            bleWriting = false;
        }
    }

    private void closeClassic() {
        classicOutput = null;
        if (classicSocket != null) {
            try {
                classicSocket.close();
            } catch (IOException ignored) {
            }
            classicSocket = null;
        }
    }

    private void log(String message) {
        handler.post(() -> status.setText(message + "\n" + status.getText()));
    }
}
