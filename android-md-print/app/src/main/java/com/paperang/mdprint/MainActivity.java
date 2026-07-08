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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class MainActivity extends Activity {
    private static final int EDIT_REQUEST = 1001;
    private static final UUID PAPERANG_SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455");
    private static final UUID PAPERANG_WRITE_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3");
    private static final UUID PAPERANG_NOTIFY_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private static final int WIDTH_PX = 384;
    private static final int WIDTH_BYTES = 48;
    private static final int PAPER_PADDING_PX = 22;
    private static final int CONTENT_WIDTH_PX = WIDTH_PX - PAPER_PADDING_PX * 2;
    private static final int BLOCK_GAP_PX = 8;
    private static final int THERMAL_THRESHOLD = 190;
    private static final int TABLE_BORDER_PX = 2;
    private static final int TABLE_CELL_PADDING_X = 5;
    private static final int TABLE_CELL_PADDING_Y = 5;
    private static final float TABLE_TEXT_SCALE = 0.82f;
    private static final float DEFAULT_CONTENT_TEXT_PX = 19f;
    private static final float ADVANCE_MM_PER_PX = 0.1217f;
    private static final int FEED_UNITS_PER_MM = 56;
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
    private TextView densityStatus;
    private TextView feedStatus;
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
    private int printDensity = 75;
    private float postPrintFeedMm = 5f;

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

        LinearLayout statusCard = panel(Color.rgb(247, 249, 247), Color.rgb(198, 209, 203));
        statusCard.setPadding(12, 12, 12, 12);
        LinearLayout.LayoutParams statusCardParams = new LinearLayout.LayoutParams(-1, -2);
        statusCardParams.setMargins(0, 10, 0, 0);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setPadding(0, 0, 0, 8);
        connectionStatus = chip("未连接", Color.rgb(255, 255, 255), Color.rgb(31, 44, 41));
        routeStatus = chip("通道：无", Color.rgb(213, 231, 223), Color.rgb(31, 44, 41));
        statusRow.addView(connectionStatus, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams routeParams = new LinearLayout.LayoutParams(0, -2, 1);
        routeParams.setMargins(10, 0, 0, 0);
        statusRow.addView(routeStatus, routeParams);
        statusCard.addView(statusRow);

        estimateStatus = chip("纸宽 384px · 高度待计算 · 0.1217mm/px", Color.rgb(255, 251, 235), Color.rgb(62, 49, 25));
        statusCard.addView(estimateStatus, new LinearLayout.LayoutParams(-1, -2));

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
        statusCard.addView(sizeRow);

        LinearLayout densityRow = new LinearLayout(this);
        densityRow.setOrientation(LinearLayout.HORIZONTAL);
        densityRow.setPadding(0, 8, 0, 0);
        Button densityDown = actionButton("浓度 -");
        densityDown.setOnClickListener(v -> adjustDensity(-5));
        densityStatus = chip("浓度 75", Color.rgb(255, 255, 255), Color.rgb(31, 44, 41));
        Button densityUp = actionButton("浓度 +");
        densityUp.setOnClickListener(v -> adjustDensity(5));
        densityRow.addView(densityDown, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams densityParams = new LinearLayout.LayoutParams(0, -2, 1);
        densityParams.setMargins(8, 0, 0, 0);
        densityRow.addView(densityStatus, densityParams);
        addRowButton(densityRow, densityUp);
        statusCard.addView(densityRow);

        LinearLayout feedRow = new LinearLayout(this);
        feedRow.setOrientation(LinearLayout.HORIZONTAL);
        feedRow.setPadding(0, 8, 0, 0);
        Button feedDown = actionButton("尾纸 -");
        feedDown.setOnClickListener(v -> adjustFeed(-1f));
        feedStatus = chip("尾纸 5mm", Color.rgb(255, 255, 255), Color.rgb(31, 44, 41));
        Button feedUp = actionButton("尾纸 +");
        feedUp.setOnClickListener(v -> adjustFeed(1f));
        feedRow.addView(feedDown, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams feedParams = new LinearLayout.LayoutParams(0, -2, 1);
        feedParams.setMargins(8, 0, 0, 0);
        feedRow.addView(feedStatus, feedParams);
        addRowButton(feedRow, feedUp);
        statusCard.addView(feedRow);
        root.addView(statusCard, statusCardParams);

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
        previewPanel.setBackgroundColor(Color.rgb(229, 234, 231));
        previewPanel.setPadding(18, 18, 18, 28);
        previewPanel.addView(preview, new ScrollView.LayoutParams(WIDTH_PX, -2));
        previewScroller = new HorizontalScrollView(this);
        previewScroller.setFillViewport(false);
        previewScroller.setBackground(cardBackground(Color.rgb(224, 230, 226), Color.rgb(184, 196, 190), 8));
        previewScroller.addView(previewPanel, new HorizontalScrollView.LayoutParams(-2, -1));
        previewScroller.setVisibility(View.GONE);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1);
        previewParams.setMargins(0, 10, 0, 10);
        root.addView(previewScroller, previewParams);

        LinearLayout connectRow = new LinearLayout(this);
        connectRow.setOrientation(LinearLayout.HORIZONTAL);
        connectRow.setPadding(0, 0, 0, 8);
        Button classicConnect = primaryButton("连接打印机");
        classicConnect.setOnClickListener(v -> connectFirstPairedClassicDevice());
        Button bleConnect = actionButton("BLE 备用");
        bleConnect.setOnClickListener(v -> startBleScan());
        connectRow.addView(classicConnect, new LinearLayout.LayoutParams(0, -2, 2));
        addRowButton(connectRow, bleConnect);
        root.addView(connectRow);

        LinearLayout printRow = new LinearLayout(this);
        printRow.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = actionButton("编辑");
        edit.setOnClickListener(v -> openEditorPage());
        Button print = primaryButton("打印 Markdown");
        print.setOnClickListener(v -> printMarkdown());
        printRow.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        addRowButton(printRow, print);
        root.addView(printRow);

        status = new TextView(this);
        status.setTextSize(11);
        status.setTextColor(Color.rgb(45, 57, 54));
        status.setPadding(14, 12, 14, 12);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(cardBackground(Color.rgb(247, 249, 247), Color.rgb(204, 214, 209), 8));
        scroll.addView(status);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(-1, 118);
        logParams.setMargins(0, 10, 0, 0);
        root.addView(scroll, logParams);

        setContentView(root);
        showPreview();
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
        openEditorPage();
    }

    private void openEditorPage() {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_MARKDOWN, editor.getText().toString());
        startActivityForResult(intent, EDIT_REQUEST);
    }

    private void showPreview() {
        updatePreview();
        if (editorPanel != null) {
            editorPanel.setVisibility(View.GONE);
        }
        previewScroller.setVisibility(View.VISIBLE);
        if (editTab != null && previewTab != null) {
            editTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            previewTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            editTab.setBackground(cardBackground(Color.rgb(246, 248, 246), Color.rgb(177, 191, 184), 8));
            editTab.setTextColor(Color.rgb(23, 34, 31));
            previewTab.setBackground(cardBackground(Color.rgb(29, 88, 74), Color.rgb(29, 88, 74), 8));
            previewTab.setTextColor(Color.WHITE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_REQUEST && data != null && data.hasExtra(EditorActivity.EXTRA_MARKDOWN)) {
            editor.setText(data.getStringExtra(EditorActivity.EXTRA_MARKDOWN));
            showPreview();
        }
    }

    private void updatePreview() {
        markdownRenderer().setMarkdown(preview, prepareMarkdownForPaper(editor.getText().toString()));
        updateEstimate(measureThermalHeight(editor.getText().toString()));
    }

    private void adjustTextSize(float deltaPx) {
        contentTextPx = Math.max(12f, Math.min(28f, contentTextPx + deltaPx));
        if (fontStatus != null) {
            fontStatus.setText("字号 " + Math.round(contentTextPx) + "px");
        }
        markwon = null;
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTextPx);
        if (previewScroller.getVisibility() == View.VISIBLE) {
            updatePreview();
        } else {
            updateEstimate();
        }
    }

    private void adjustDensity(int delta) {
        printDensity = Math.max(0, Math.min(255, printDensity + delta));
        if (densityStatus != null) {
            densityStatus.setText("浓度 " + printDensity);
        }
        if (readySilently()) {
            sendRaw(pack(25, new byte[]{(byte) printDensity}, 0));
            log("已设置打印浓度：" + printDensity);
        }
    }

    private void adjustFeed(float deltaMm) {
        postPrintFeedMm = Math.max(0f, Math.min(20f, postPrintFeedMm + deltaMm));
        if (feedStatus != null) {
            feedStatus.setText("尾纸 " + formatMm(postPrintFeedMm) + "mm");
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
        sendRaw(pack(25, new byte[]{(byte) printDensity}, 0));
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
        if (paper.tableCount > 0) {
            log("表格已使用热敏模式重排：" + paper.tableCount + " 个。");
        }
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
        sendRaw(pack(26, int16le(feedUnits()), 0));
        int heightPx = image.length / WIDTH_BYTES;
        log("已入队：宽 384px，高 " + heightPx + "px，数据 " + image.length + " bytes，内容约 " + formatMm(estimateLengthMm(heightPx)) + "mm，尾纸 " + formatMm(postPrintFeedMm) + "mm");
    }

    private RenderedPaper renderMarkdown(String markdown) {
        List<PrintBlock> blocks = parsePrintBlocks(markdown);
        List<BlockBitmap> bitmaps = new ArrayList<>();
        int height = PAPER_PADDING_PX * 2;
        int tableCount = 0;

        for (PrintBlock block : blocks) {
            Bitmap blockBitmap = block.table == null
                    ? renderTextBlock(block.text)
                    : renderTableBlock(block.table);
            if (blockBitmap == null || blockBitmap.getHeight() == 0) {
                continue;
            }
            if (block.table != null) {
                tableCount++;
            }
            if (!bitmaps.isEmpty()) {
                height += BLOCK_GAP_PX;
            }
            bitmaps.add(new BlockBitmap(blockBitmap));
            height += blockBitmap.getHeight();
        }

        height = Math.max(MIN_PRINT_HEIGHT_PX, height);
        if (height > MAX_PRINT_HEIGHT_PX) {
            height = MAX_PRINT_HEIGHT_PX;
            log("内容超过最大渲染高度，已截到 " + MAX_PRINT_HEIGHT_PX + "px。");
        }

        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        int y = PAPER_PADDING_PX;
        for (int i = 0; i < bitmaps.size(); i++) {
            if (i > 0) {
                y += BLOCK_GAP_PX;
            }
            Bitmap blockBitmap = bitmaps.get(i).bitmap;
            if (y >= height) {
                break;
            }
            canvas.drawBitmap(blockBitmap, PAPER_PADDING_PX, y, null);
            y += blockBitmap.getHeight();
        }

        return new RenderedPaper(encodeBitmap(bitmap), height, tableCount);
    }

    private TextView createPaperView() {
        TextView paper = new TextView(this);
        paper.setTextColor(Color.BLACK);
        paper.setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTextPx);
        paper.setIncludeFontPadding(false);
        paper.setPadding(PAPER_PADDING_PX, PAPER_PADDING_PX, PAPER_PADDING_PX, PAPER_PADDING_PX);
        paper.setLineSpacing(2f, 1.12f);
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

    private TextView createContentView() {
        TextView view = new TextView(this);
        view.setTextColor(Color.BLACK);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, contentTextPx);
        view.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setPadding(0, 0, 0, 0);
        view.setLineSpacing(2f, 1.14f);
        view.setBackgroundColor(Color.WHITE);
        view.setMinWidth(CONTENT_WIDTH_PX);
        view.setMaxWidth(CONTENT_WIDTH_PX);
        view.setWidth(CONTENT_WIDTH_PX);
        return view;
    }

    private int measureContentView(TextView view) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(CONTENT_WIDTH_PX, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        int height = Math.max(0, view.getMeasuredHeight());
        view.layout(0, 0, CONTENT_WIDTH_PX, height);
        return height;
    }

    private Bitmap renderTextBlock(String markdown) {
        if (markdown == null || markdown.trim().length() == 0) {
            return null;
        }
        TextView view = createContentView();
        markdownRenderer().setMarkdown(view, prepareMarkdownForPaper(markdown.trim()));
        int height = measureContentView(view);
        if (height <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(CONTENT_WIDTH_PX, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        return bitmap;
    }

    private int measureTextBlockHeight(String markdown) {
        if (markdown == null || markdown.trim().length() == 0) {
            return 0;
        }
        TextView view = createContentView();
        markdownRenderer().setMarkdown(view, prepareMarkdownForPaper(markdown.trim()));
        return measureContentView(view);
    }

    private void updateEstimate() {
        updateEstimate(measureThermalHeight(editor.getText().toString()));
    }

    private String prepareMarkdownForPaper(String markdown) {
        String normalized = markdown.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder(normalized.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            String line = prepareLineMathForPreview(lines[i]);
            out.append(line.indexOf('|') >= 0 ? addTableBreakpoints(line) : line);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private String addTableBreakpoints(String line) {
        StringBuilder out = new StringBuilder(line.length() + 8);
        int run = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            boolean asciiWord = c < 128 && Character.isLetterOrDigit(c);
            if (asciiWord) {
                run++;
                if (run > 0 && run % 10 == 0) {
                    out.append('\u200B');
                }
            } else {
                run = 0;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String prepareLineMathForPreview(String line) {
        if (line == null || line.length() == 0) {
            return "";
        }
        if (line.indexOf('|') >= 0 && !isLooseSeparatorLine(line)) {
            String trimmed = line.trim();
            boolean leadingPipe = trimmed.startsWith("|");
            boolean trailingPipe = trimmed.endsWith("|");
            String[] cells = splitTableCells(line);
            StringBuilder out = new StringBuilder(line.length() + 16);
            if (leadingPipe) {
                out.append("| ");
            }
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    out.append(" | ");
                }
                out.append(prepareMathCellForPreview(cells[i].trim()));
            }
            if (trailingPipe) {
                out.append(" |");
            }
            return out.toString();
        }

        String trimmed = line.trim();
        if (looksLikeFormula(trimmed) && isMostlyFormulaText(trimmed) && !hasMathDelimiters(trimmed)) {
            int leading = line.indexOf(trimmed);
            String prefix = leading > 0 ? line.substring(0, leading) : "";
            return prefix + "$" + looseMathToLatex(trimmed) + "$";
        }
        return line;
    }

    private String prepareMathCellForPreview(String cell) {
        if (!looksLikeFormula(cell) || hasMathDelimiters(cell)) {
            return cell;
        }
        return "$" + looseMathToLatex(cell) + "$";
    }

    private boolean isLooseSeparatorLine(String line) {
        String[] cells = splitTableCells(line);
        if (cells.length < 2) {
            return false;
        }
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (!trimmed.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMathDelimiters(String text) {
        if (text == null) {
            return false;
        }
        return text.indexOf('$') >= 0 || text.indexOf("\\(") >= 0 || text.indexOf("\\[") >= 0;
    }

    private boolean looksLikeFormula(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 2) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (hasMathDelimiters(t) || lower.contains("frac{") || lower.contains("\\frac")
                || lower.contains("sqrt{") || lower.contains("\\sqrt")) {
            return true;
        }
        if (t.indexOf('=') >= 0 && (lower.matches(".*[a-z]\\s*\\([^)]*\\).*")
                || lower.matches(".*\\b[a-z]\\b.*") || t.indexOf('^') >= 0 || t.indexOf('_') >= 0)) {
            return true;
        }
        if (t.indexOf('^') >= 0 || t.indexOf('_') >= 0) {
            return true;
        }
        return lower.matches(".*\\b(alpha|beta|gamma|delta|theta|lambda|mu|xi|pi|rho|sigma|tau|phi|omega)\\b.*")
                && t.matches(".*[=+\\-*/^_()].*");
    }

    private boolean isMostlyFormulaText(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                return false;
            }
        }
        return true;
    }

    private String looseMathToLatex(String text) {
        String out = stripMathDelimiters(text == null ? "" : text.trim());
        out = addMissingLatexCommandSlash(out, "frac");
        out = addMissingLatexCommandSlash(out, "sqrt");
        out = replaceGreekNames(out, true);
        return out;
    }

    private String addMissingLatexCommandSlash(String text, String command) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            if (regionMatchesIgnoreCase(text, i, command)
                    && (i == 0 || text.charAt(i - 1) != '\\')
                    && (i == 0 || !Character.isLetter(text.charAt(i - 1)))) {
                int after = i + command.length();
                int brace = skipSpaces(text, after);
                if (brace < text.length() && text.charAt(brace) == '{') {
                    out.append('\\').append(command);
                    i += command.length();
                    continue;
                }
            }
            out.append(text.charAt(i));
            i++;
        }
        return out.toString();
    }

    private int measureThermalHeight(String markdown) {
        List<PrintBlock> blocks = parsePrintBlocks(markdown);
        int height = PAPER_PADDING_PX * 2;
        int visibleBlocks = 0;
        for (PrintBlock block : blocks) {
            int blockHeight = block.table == null
                    ? measureTextBlockHeight(block.text)
                    : layoutTable(block.table).height;
            if (blockHeight <= 0) {
                continue;
            }
            if (visibleBlocks > 0) {
                height += BLOCK_GAP_PX;
            }
            height += blockHeight;
            visibleBlocks++;
        }
        return Math.min(MAX_PRINT_HEIGHT_PX, Math.max(MIN_PRINT_HEIGHT_PX, height));
    }

    private List<PrintBlock> parsePrintBlocks(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<PrintBlock> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        int i = 0;
        while (i < lines.length) {
            if (i + 1 < lines.length && isTableHeader(lines[i], lines[i + 1])) {
                flushTextBlock(blocks, text);
                List<String> tableLines = new ArrayList<>();
                tableLines.add(lines[i]);
                tableLines.add(lines[i + 1]);
                i += 2;
                while (i < lines.length && lines[i].trim().length() > 0 && lines[i].indexOf('|') >= 0) {
                    tableLines.add(lines[i]);
                    i++;
                }
                blocks.add(PrintBlock.table(parseTableBlock(tableLines)));
                continue;
            }

            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(lines[i]);
            i++;
        }
        flushTextBlock(blocks, text);
        return blocks;
    }

    private void flushTextBlock(List<PrintBlock> blocks, StringBuilder text) {
        if (text.length() > 0 && text.toString().trim().length() > 0) {
            blocks.add(PrintBlock.text(text.toString()));
        }
        text.setLength(0);
    }

    private boolean isTableHeader(String headerLine, String separatorLine) {
        if (headerLine == null || headerLine.indexOf('|') < 0) {
            return false;
        }
        String[] headers = splitTableCells(headerLine);
        if (headers.length < 2) {
            return false;
        }
        return isSeparatorLine(separatorLine, headers.length);
    }

    private boolean isSeparatorLine(String line, int expectedColumns) {
        String[] cells = splitTableCells(line);
        if (cells.length < 2 || cells.length < expectedColumns) {
            return false;
        }
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (!trimmed.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private String[] splitTableCells(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.split("\\|", -1);
    }

    private TableBlock parseTableBlock(List<String> lines) {
        String[] headerCells = splitTableCells(lines.get(0));
        String[] separatorCells = splitTableCells(lines.get(1));
        int columns = Math.max(headerCells.length, separatorCells.length);
        TableBlock table = new TableBlock(columns);
        for (int i = 0; i < columns; i++) {
            table.align[i] = parseTableAlign(i < separatorCells.length ? separatorCells[i] : "");
        }
        table.rows.add(new TableRow(normalizeTableCells(headerCells, columns), true));
        for (int i = 2; i < lines.size(); i++) {
            table.rows.add(new TableRow(normalizeTableCells(splitTableCells(lines.get(i)), columns), false));
        }
        return table;
    }

    private int parseTableAlign(String separator) {
        String trimmed = separator == null ? "" : separator.trim();
        boolean left = trimmed.startsWith(":");
        boolean right = trimmed.endsWith(":");
        if (left && right) {
            return TableBlock.ALIGN_CENTER;
        }
        if (right) {
            return TableBlock.ALIGN_RIGHT;
        }
        return TableBlock.ALIGN_LEFT;
    }

    private String[] normalizeTableCells(String[] cells, int columns) {
        String[] out = new String[columns];
        for (int i = 0; i < columns; i++) {
            out[i] = cleanTableCell(i < cells.length ? cells[i] : "");
        }
        return out;
    }

    private String cleanTableCell(String cell) {
        String cleaned = cell == null ? "" : cell.trim();
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", " ");
        cleaned = cleaned.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1");
        boolean formula = looksLikeFormula(cleaned);
        cleaned = cleaned.replace("`", "");
        cleaned = cleaned.replace("**", "");
        cleaned = cleaned.replace("__", "");
        if (formula) {
            cleaned = formulaToThermalText(cleaned);
        } else {
            cleaned = cleaned.replace("*", "");
            cleaned = cleaned.replace("_", "");
        }
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.trim();
    }

    private String formulaToThermalText(String text) {
        return formulaToThermalText(text, 0);
    }

    private String formulaToThermalText(String text, int depth) {
        if (text == null) {
            return "";
        }
        if (depth > 8) {
            return text;
        }
        String out = stripMathDelimiters(text.trim());
        out = replaceFractionsPlain(out, depth);
        out = replaceSqrtPlain(out, depth);
        out = replaceLatexCommandsPlain(out);
        out = replaceGreekNames(out, false);
        out = replaceScriptsPlain(out, '^', true);
        out = replaceScriptsPlain(out, '_', false);
        out = out.replace("*", "\u00d7");
        out = out.replace("{", "").replace("}", "");
        out = out.replaceAll("\\s+", " ");
        return out.trim();
    }

    private String stripMathDelimiters(String text) {
        String out = text == null ? "" : text.trim();
        while (out.startsWith("$$") && out.endsWith("$$") && out.length() >= 4) {
            out = out.substring(2, out.length() - 2).trim();
        }
        while (out.startsWith("$") && out.endsWith("$") && out.length() >= 2) {
            out = out.substring(1, out.length() - 1).trim();
        }
        out = out.replace("\\(", "").replace("\\)", "");
        out = out.replace("\\[", "").replace("\\]", "");
        return out;
    }

    private String replaceFractionsPlain(String text, int depth) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int start = findNextCommand(text, i, "frac");
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int commandStart = text.charAt(start) == '\\' ? start + 1 : start;
            int firstBrace = skipSpaces(text, commandStart + 4);
            if (firstBrace >= text.length() || text.charAt(firstBrace) != '{') {
                out.append(text.charAt(start));
                i = start + 1;
                continue;
            }
            int firstEnd = findMatchingBrace(text, firstBrace);
            if (firstEnd < 0) {
                out.append(text.substring(start));
                break;
            }
            int secondBrace = skipSpaces(text, firstEnd + 1);
            if (secondBrace >= text.length() || text.charAt(secondBrace) != '{') {
                out.append(text.substring(start, firstEnd + 1));
                i = firstEnd + 1;
                continue;
            }
            int secondEnd = findMatchingBrace(text, secondBrace);
            if (secondEnd < 0) {
                out.append(text.substring(start));
                break;
            }
            String numerator = text.substring(firstBrace + 1, firstEnd);
            String denominator = text.substring(secondBrace + 1, secondEnd);
            out.append('(')
                    .append(formulaToThermalText(numerator, depth + 1))
                    .append(")/(")
                    .append(formulaToThermalText(denominator, depth + 1))
                    .append(')');
            i = secondEnd + 1;
        }
        return out.toString();
    }

    private String replaceSqrtPlain(String text, int depth) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int start = findNextCommand(text, i, "sqrt");
            if (start < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, start);
            int commandStart = text.charAt(start) == '\\' ? start + 1 : start;
            int firstBrace = skipSpaces(text, commandStart + 4);
            if (firstBrace >= text.length() || text.charAt(firstBrace) != '{') {
                out.append(text.charAt(start));
                i = start + 1;
                continue;
            }
            int firstEnd = findMatchingBrace(text, firstBrace);
            if (firstEnd < 0) {
                out.append(text.substring(start));
                break;
            }
            String body = text.substring(firstBrace + 1, firstEnd);
            out.append('\u221a').append('(').append(formulaToThermalText(body, depth + 1)).append(')');
            i = firstEnd + 1;
        }
        return out.toString();
    }

    private int findNextCommand(String text, int from, String command) {
        for (int i = Math.max(0, from); i < text.length(); i++) {
            int commandStart = text.charAt(i) == '\\' ? i + 1 : i;
            if (!regionMatchesIgnoreCase(text, commandStart, command)) {
                continue;
            }
            if (commandStart > 0 && text.charAt(commandStart - 1) != '\\'
                    && Character.isLetter(text.charAt(commandStart - 1))) {
                continue;
            }
            int after = commandStart + command.length();
            if (after < text.length() && Character.isLetter(text.charAt(after))) {
                continue;
            }
            int brace = skipSpaces(text, after);
            if (brace < text.length() && text.charAt(brace) == '{') {
                return i;
            }
        }
        return -1;
    }

    private int skipSpaces(String text, int from) {
        int i = Math.max(0, from);
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private int findMatchingBrace(String text, int openBrace) {
        if (openBrace < 0 || openBrace >= text.length() || text.charAt(openBrace) != '{') {
            return -1;
        }
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean regionMatchesIgnoreCase(String text, int offset, String needle) {
        return offset >= 0 && offset + needle.length() <= text.length()
                && text.regionMatches(true, offset, needle, 0, needle.length());
    }

    private String replaceLatexCommandsPlain(String text) {
        String out = text;
        String[][] replacements = {
                {"\\alpha", "\u03b1"}, {"\\beta", "\u03b2"}, {"\\gamma", "\u03b3"},
                {"\\delta", "\u03b4"}, {"\\epsilon", "\u03b5"}, {"\\varepsilon", "\u03b5"},
                {"\\zeta", "\u03b6"}, {"\\eta", "\u03b7"}, {"\\theta", "\u03b8"},
                {"\\vartheta", "\u03b8"}, {"\\lambda", "\u03bb"}, {"\\mu", "\u03bc"},
                {"\\nu", "\u03bd"}, {"\\xi", "\u03be"}, {"\\pi", "\u03c0"},
                {"\\rho", "\u03c1"}, {"\\sigma", "\u03c3"}, {"\\tau", "\u03c4"},
                {"\\phi", "\u03c6"}, {"\\varphi", "\u03c6"}, {"\\omega", "\u03c9"},
                {"\\Delta", "\u0394"}, {"\\Omega", "\u03a9"}, {"\\Sigma", "\u03a3"},
                {"\\cdot", "\u00b7"}, {"\\times", "\u00d7"}, {"\\div", "\u00f7"},
                {"\\leq", "\u2264"}, {"\\le", "\u2264"}, {"\\geq", "\u2265"},
                {"\\ge", "\u2265"}, {"\\neq", "\u2260"}, {"\\ne", "\u2260"},
                {"\\approx", "\u2248"}, {"\\sim", "\u223c"}, {"\\pm", "\u00b1"},
                {"\\mp", "\u2213"}, {"\\to", "\u2192"}, {"\\rightarrow", "\u2192"},
                {"\\left", ""}, {"\\right", ""}, {"\\,", ""}, {"\\;", " "},
                {"\\:", " "}, {"\\ ", " "}
        };
        for (String[] pair : replacements) {
            out = out.replace(pair[0], pair[1]);
        }
        return out;
    }

    private String replaceGreekNames(String text, boolean latex) {
        String[][] names = {
                {"varepsilon", latex ? "\\varepsilon" : "\u03b5"},
                {"vartheta", latex ? "\\vartheta" : "\u03b8"},
                {"epsilon", latex ? "\\epsilon" : "\u03b5"},
                {"varphi", latex ? "\\varphi" : "\u03c6"},
                {"alpha", latex ? "\\alpha" : "\u03b1"},
                {"gamma", latex ? "\\gamma" : "\u03b3"},
                {"delta", latex ? "\\delta" : "\u03b4"},
                {"theta", latex ? "\\theta" : "\u03b8"},
                {"lambda", latex ? "\\lambda" : "\u03bb"},
                {"omega", latex ? "\\omega" : "\u03c9"},
                {"sigma", latex ? "\\sigma" : "\u03c3"},
                {"beta", latex ? "\\beta" : "\u03b2"},
                {"zeta", latex ? "\\zeta" : "\u03b6"},
                {"eta", latex ? "\\eta" : "\u03b7"},
                {"mu", latex ? "\\mu" : "\u03bc"},
                {"nu", latex ? "\\nu" : "\u03bd"},
                {"xi", latex ? "\\xi" : "\u03be"},
                {"pi", latex ? "\\pi" : "\u03c0"},
                {"rho", latex ? "\\rho" : "\u03c1"},
                {"tau", latex ? "\\tau" : "\u03c4"},
                {"phi", latex ? "\\phi" : "\u03c6"}
        };

        StringBuilder out = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            if (i > 0 && text.charAt(i - 1) == '\\') {
                out.append(text.charAt(i));
                i++;
                continue;
            }
            boolean matched = false;
            for (String[] pair : names) {
                String name = pair[0];
                if (regionMatchesIgnoreCase(text, i, name)) {
                    out.append(pair[1]);
                    i += name.length();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    private String replaceScriptsPlain(String text, char marker, boolean superscript) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != marker || i + 1 >= text.length()) {
                out.append(c);
                i++;
                continue;
            }

            int next = i + 1;
            String script;
            if (text.charAt(next) == '{') {
                int end = findMatchingBrace(text, next);
                if (end < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                script = text.substring(next + 1, end);
                i = end + 1;
            } else {
                script = String.valueOf(text.charAt(next));
                i = next + 1;
            }
            out.append(convertScript(script, superscript));
        }
        return out.toString();
    }

    private String convertScript(String script, boolean superscript) {
        StringBuilder out = new StringBuilder(script.length());
        for (int i = 0; i < script.length(); i++) {
            String mapped = superscript ? superscriptChar(script.charAt(i)) : subscriptChar(script.charAt(i));
            if (mapped == null) {
                return (superscript ? "^(" : "_(") + script + ")";
            }
            out.append(mapped);
        }
        return out.toString();
    }

    private String superscriptChar(char c) {
        switch (c) {
            case '0': return "\u2070";
            case '1': return "\u00b9";
            case '2': return "\u00b2";
            case '3': return "\u00b3";
            case '4': return "\u2074";
            case '5': return "\u2075";
            case '6': return "\u2076";
            case '7': return "\u2077";
            case '8': return "\u2078";
            case '9': return "\u2079";
            case '+': return "\u207a";
            case '-': return "\u207b";
            case '=': return "\u207c";
            case '(': return "\u207d";
            case ')': return "\u207e";
            case 'n': return "\u207f";
            case 'i': return "\u2071";
            default: return null;
        }
    }

    private String subscriptChar(char c) {
        switch (c) {
            case '0': return "\u2080";
            case '1': return "\u2081";
            case '2': return "\u2082";
            case '3': return "\u2083";
            case '4': return "\u2084";
            case '5': return "\u2085";
            case '6': return "\u2086";
            case '7': return "\u2087";
            case '8': return "\u2088";
            case '9': return "\u2089";
            case '+': return "\u208a";
            case '-': return "\u208b";
            case '=': return "\u208c";
            case '(': return "\u208d";
            case ')': return "\u208e";
            case 'a': return "\u2090";
            case 'e': return "\u2091";
            case 'h': return "\u2095";
            case 'i': return "\u1d62";
            case 'j': return "\u2c7c";
            case 'k': return "\u2096";
            case 'l': return "\u2097";
            case 'm': return "\u2098";
            case 'n': return "\u2099";
            case 'o': return "\u2092";
            case 'p': return "\u209a";
            case 'r': return "\u1d63";
            case 's': return "\u209b";
            case 't': return "\u209c";
            case 'u': return "\u1d64";
            case 'v': return "\u1d65";
            case 'x': return "\u2093";
            default: return null;
        }
    }

    private Bitmap renderTableBlock(TableBlock table) {
        TableLayout layout = layoutTable(table);
        if (layout.height <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(CONTENT_WIDTH_PX, layout.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint normalPaint = tableTextPaint(false);
        Paint headerPaint = tableTextPaint(true);
        int y = TABLE_BORDER_PX;
        for (RowLayout row : layout.rows) {
            int x = TABLE_BORDER_PX;
            Paint textPaint = row.header ? headerPaint : normalPaint;
            Paint.FontMetricsInt metrics = textPaint.getFontMetricsInt();
            int baselineStart = y + TABLE_CELL_PADDING_Y - metrics.ascent;
            for (int col = 0; col < table.columns; col++) {
                List<String> lines = row.cells.get(col);
                int baseline = baselineStart;
                for (String line : lines) {
                    float drawX = tableTextX(line, textPaint, x, layout.widths[col], table.align[col]);
                    canvas.drawText(line, drawX, baseline, textPaint);
                    baseline += layout.lineHeight;
                }
                x += layout.widths[col] + TABLE_BORDER_PX;
            }
            y += row.height + TABLE_BORDER_PX;
        }

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(false);
        drawTableGrid(canvas, layout, borderPaint);
        return bitmap;
    }

    private TableLayout layoutTable(TableBlock table) {
        TableLayout layout = new TableLayout();
        layout.widths = tableColumnWidths(table);
        Paint normalPaint = tableTextPaint(false);
        Paint headerPaint = tableTextPaint(true);
        layout.lineHeight = Math.max(tableLineHeight(normalPaint), tableLineHeight(headerPaint));
        layout.height = TABLE_BORDER_PX;

        for (TableRow row : table.rows) {
            RowLayout rowLayout = new RowLayout(row.header);
            Paint paint = row.header ? headerPaint : normalPaint;
            int rowHeight = layout.lineHeight + TABLE_CELL_PADDING_Y * 2;
            for (int col = 0; col < table.columns; col++) {
                int textWidth = Math.max(8, layout.widths[col] - TABLE_CELL_PADDING_X * 2);
                List<String> lines = wrapTableText(row.cells[col], paint, textWidth);
                rowLayout.cells.add(lines);
                rowHeight = Math.max(rowHeight, lines.size() * layout.lineHeight + TABLE_CELL_PADDING_Y * 2);
            }
            rowLayout.height = rowHeight;
            layout.rows.add(rowLayout);
            layout.height += rowHeight + TABLE_BORDER_PX;
        }
        return layout;
    }

    private int[] tableColumnWidths(TableBlock table) {
        int columns = Math.max(1, table.columns);
        int innerWidth = CONTENT_WIDTH_PX - TABLE_BORDER_PX * (columns + 1);
        int[] widths = new int[columns];
        if (innerWidth <= columns * 8) {
            int equal = Math.max(8, CONTENT_WIDTH_PX / columns - TABLE_BORDER_PX);
            for (int i = 0; i < columns; i++) {
                widths[i] = equal;
            }
            return widths;
        }

        int minWidth = Math.max(28, Math.min(58, innerWidth / Math.max(1, columns * 2)));
        if (minWidth * columns > innerWidth) {
            int equal = innerWidth / columns;
            int used = 0;
            for (int i = 0; i < columns; i++) {
                widths[i] = i == columns - 1 ? innerWidth - used : equal;
                used += widths[i];
            }
            return widths;
        }

        int[] weights = new int[columns];
        int weightSum = 0;
        for (int col = 0; col < columns; col++) {
            int weight = 4;
            for (TableRow row : table.rows) {
                weight = Math.max(weight, displayUnits(row.cells[col]));
            }
            weight = Math.max(4, Math.min(24, weight));
            weights[col] = weight;
            weightSum += weight;
        }

        int flex = innerWidth - minWidth * columns;
        int used = 0;
        for (int col = 0; col < columns; col++) {
            widths[col] = minWidth + Math.round(flex * (weights[col] / (float) weightSum));
            used += widths[col];
        }
        int diff = innerWidth - used;
        int col = columns - 1;
        while (diff != 0) {
            int delta = diff > 0 ? 1 : -1;
            if (widths[col] + delta >= 8) {
                widths[col] += delta;
                diff -= delta;
            }
            col--;
            if (col < 0) {
                col = columns - 1;
            }
        }
        return widths;
    }

    private int displayUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += text.charAt(i) < 128 ? 1 : 2;
        }
        return Math.max(1, units);
    }

    private Paint tableTextPaint(boolean header) {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setSubpixelText(false);
        paint.setTextSize(Math.max(14f, contentTextPx * TABLE_TEXT_SCALE));
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, header ? Typeface.BOLD : Typeface.NORMAL));
        paint.setFakeBoldText(header);
        return paint;
    }

    private int tableLineHeight(Paint paint) {
        Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
        return Math.max(16, metrics.descent - metrics.ascent + 2);
    }

    private List<String> wrapTableText(String text, Paint paint, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.length() == 0) {
            lines.add("");
            return lines;
        }

        String[] tokens = cleaned.split("\\s+");
        String current = "";
        for (String token : tokens) {
            if (token.length() == 0) {
                continue;
            }
            if (current.length() == 0) {
                current = paint.measureText(token) <= maxWidth
                        ? token
                        : appendWrappedToken(lines, token, paint, maxWidth);
                continue;
            }

            String candidate = current + " " + token;
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate;
            } else {
                lines.add(current);
                current = paint.measureText(token) <= maxWidth
                        ? token
                        : appendWrappedToken(lines, token, paint, maxWidth);
            }
        }
        if (current.length() > 0) {
            lines.add(current);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private String appendWrappedToken(List<String> lines, String token, Paint paint, int maxWidth) {
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            String candidate = current.toString() + c;
            if (current.length() == 0 || paint.measureText(candidate) <= maxWidth) {
                current.append(c);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(c);
            }
        }
        return current.toString();
    }

    private float tableTextX(String line, Paint paint, int cellLeft, int cellWidth, int align) {
        float textWidth = paint.measureText(line);
        float minX = cellLeft + TABLE_CELL_PADDING_X;
        if (align == TableBlock.ALIGN_RIGHT) {
            return Math.max(minX, cellLeft + cellWidth - TABLE_CELL_PADDING_X - textWidth);
        }
        if (align == TableBlock.ALIGN_CENTER) {
            return Math.max(minX, cellLeft + (cellWidth - textWidth) / 2f);
        }
        return minX;
    }

    private void drawTableGrid(Canvas canvas, TableLayout layout, Paint paint) {
        int y = 0;
        canvas.drawRect(0, y, CONTENT_WIDTH_PX, y + TABLE_BORDER_PX, paint);
        y += TABLE_BORDER_PX;
        for (RowLayout row : layout.rows) {
            y += row.height;
            canvas.drawRect(0, y, CONTENT_WIDTH_PX, y + TABLE_BORDER_PX, paint);
            y += TABLE_BORDER_PX;
        }

        int x = 0;
        canvas.drawRect(x, 0, x + TABLE_BORDER_PX, layout.height, paint);
        x += TABLE_BORDER_PX;
        for (int width : layout.widths) {
            x += width;
            canvas.drawRect(x, 0, x + TABLE_BORDER_PX, layout.height, paint);
            x += TABLE_BORDER_PX;
        }
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

    private int feedUnits() {
        return Math.max(0, Math.round(postPrintFeedMm * FEED_UNITS_PER_MM));
    }

    private String formatMm(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static class PrintBlock {
        final String text;
        final TableBlock table;

        private PrintBlock(String text, TableBlock table) {
            this.text = text;
            this.table = table;
        }

        static PrintBlock text(String text) {
            return new PrintBlock(text, null);
        }

        static PrintBlock table(TableBlock table) {
            return new PrintBlock(null, table);
        }
    }

    private static class TableBlock {
        static final int ALIGN_LEFT = 0;
        static final int ALIGN_CENTER = 1;
        static final int ALIGN_RIGHT = 2;

        final int columns;
        final int[] align;
        final List<TableRow> rows = new ArrayList<>();

        TableBlock(int columns) {
            this.columns = columns;
            this.align = new int[columns];
        }
    }

    private static class TableRow {
        final String[] cells;
        final boolean header;

        TableRow(String[] cells, boolean header) {
            this.cells = cells;
            this.header = header;
        }
    }

    private static class TableLayout {
        final List<RowLayout> rows = new ArrayList<>();
        int[] widths;
        int lineHeight;
        int height;
    }

    private static class RowLayout {
        final boolean header;
        final List<List<String>> cells = new ArrayList<>();
        int height;

        RowLayout(boolean header) {
            this.header = header;
        }
    }

    private static class BlockBitmap {
        final Bitmap bitmap;

        BlockBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

    private static class RenderedPaper {
        final byte[] bytes;
        final int heightPx;
        final int tableCount;

        RenderedPaper(byte[] bytes, int heightPx, int tableCount) {
            this.bytes = bytes;
            this.heightPx = heightPx;
            this.tableCount = tableCount;
        }
    }

    private Markwon markdownRenderer() {
        if (markwon == null) {
            markwon = Markwon.builder(this)
                    .usePlugin(new AbstractMarkwonPlugin() {
                        @Override
                        public void configureTheme(MarkwonTheme.Builder builder) {
                            builder
                                    .headingBreakHeight(0)
                                    .headingTextSizeMultipliers(new float[]{1.55f, 1.32f, 1.16f, 1.05f, 1.0f, 0.95f})
                                    .thematicBreakHeight(2)
                                    .linkColor(Color.rgb(12, 92, 118))
                                    .isLinkUnderlined(true);
                        }
                    })
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(contentTextPx, Math.max(22f, contentTextPx * 1.15f), builder -> builder.inlinesEnabled(true)))
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(TablePlugin.create(builder -> builder
                            .tableBorderColor(Color.BLACK)
                            .tableBorderWidth(2)
                            .tableCellPadding(6)
                            .tableHeaderRowBackgroundColor(Color.WHITE)
                            .tableEvenRowBackgroundColor(Color.WHITE)
                            .tableOddRowBackgroundColor(Color.WHITE)))
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
                    value = (value << 1) | (gray < THERMAL_THRESHOLD ? 1 : 0);
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
        if (readySilently()) {
            return true;
        }
        log("请先连接经典蓝牙或 BLE。");
        return false;
    }

    private boolean readySilently() {
        return classicOutput != null || bleWriteCharacteristic != null;
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
