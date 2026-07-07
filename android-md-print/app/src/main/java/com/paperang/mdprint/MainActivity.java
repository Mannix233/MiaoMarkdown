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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.zip.CRC32;

public class MainActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455");
    private static final UUID WRITE_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3");
    private static final UUID NOTIFY_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int WIDTH_PX = 384;
    private static final int WIDTH_BYTES = 48;
    private static final int STANDARD_CRC_KEY = 0x35769521;
    private static final int SESSION_CRC_KEY = 0x06968634 ^ 0x002e696d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> writeQueue = new ArrayDeque<>();
    private boolean writing = false;
    private int crcKey = STANDARD_CRC_KEY;
    private boolean crcKeySet = false;

    private EditText editor;
    private TextView status;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothLeScanner scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNeededPermissions();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText("Miao MD Print Android");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 24, 24));
        root.addView(title);

        editor = new EditText(this);
        editor.setMinLines(8);
        editor.setGravity(android.view.Gravity.TOP);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setText("题目给你什么 | 问你什么 | 你该用什么\n--- | --- | ---\n给 F(s) | 求原函数 f(t) | 拉氏反变换");
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        Button shortSample = new Button(this);
        shortSample.setText("短样例");
        shortSample.setOnClickListener(v -> editor.setText("云南我的家申报给房东方饭店第三方的个"));
        root.addView(shortSample);

        Button connect = new Button(this);
        connect.setText("扫描并连接 Paperang");
        connect.setOnClickListener(v -> startScan());
        root.addView(connect);

        Button stripe = new Button(this);
        stripe.setText("黑条测试");
        stripe.setOnClickListener(v -> printBlackStripe());
        root.addView(stripe);

        Button print = new Button(this);
        print.setText("打印 Markdown");
        print.setOnClickListener(v -> printMarkdown());
        root.addView(print);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(Color.rgb(40, 48, 48));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 260));

        setContentView(root);
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        }
    }

    private void startScan() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log("Bluetooth is disabled.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            log("No BLE scanner.");
            return;
        }
        log("Scanning...");
        scanner.startScan(scanCallback);
        handler.postDelayed(() -> {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
            }
        }, 12000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = safeDeviceName(device);
            if (name.toLowerCase(Locale.ROOT).contains("paperang") || name.toLowerCase(Locale.ROOT).contains("miao")) {
                scanner.stopScan(this);
                log("Found: " + name + " " + device.getAddress());
                connectDevice(device);
            }
        }
    };

    private void connectDevice(BluetoothDevice device) {
        if (gatt != null) {
            gatt.close();
        }
        log("Connecting...");
        gatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                log("Connected. Discovering services...");
                g.discoverServices();
            } else {
                log("Disconnected: " + statusCode);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                log("Paperang service not found.");
                return;
            }
            writeCharacteristic = service.getCharacteristic(WRITE_UUID);
            if (writeCharacteristic == null) {
                log("Write characteristic not found.");
                return;
            }
            BluetoothGattCharacteristic notify = service.getCharacteristic(NOTIFY_UUID);
            if (notify != null) {
                g.setCharacteristicNotification(notify, true);
                BluetoothGattDescriptor descriptor = notify.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(descriptor);
                }
            }
            handler.postDelayed(thisActivity()::initializePrinter, 500);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            writing = false;
            handler.postDelayed(MainActivity.this::drainQueue, 90);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] value = c.getValue();
            log("Notify cmd=" + (value.length > 1 ? (value[1] & 0xff) : -1));
        }
    };

    private MainActivity thisActivity() {
        return this;
    }

    private void initializePrinter() {
        crcKey = STANDARD_CRC_KEY;
        crcKeySet = false;
        enqueuePacket(pack(24, int32le((SESSION_CRC_KEY ^ STANDARD_CRC_KEY)), 0));
        crcKey = SESSION_CRC_KEY;
        crcKeySet = true;
        enqueuePacket(pack(34, new byte[]{0}, 0));
        enqueuePacket(pack(44, new byte[]{0}, 0));
        enqueuePacket(pack(25, new byte[]{75}, 0));
        log("Printer initialized.");
        drainQueue();
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
        sendImage(renderText(editor.getText().toString()));
    }

    private void sendImage(byte[] image) {
        enqueuePacket(pack(34, new byte[]{0}, 0));
        enqueuePacket(pack(44, new byte[]{0}, 0));
        int packetId = 0;
        for (int offset = 0; offset < image.length; offset += WIDTH_BYTES) {
            int len = Math.min(WIDTH_BYTES, image.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(image, offset, chunk, 0, len);
            enqueuePacket(pack(0, chunk, packetId++));
        }
        enqueuePacket(pack(44, new byte[]{0}, 0));
        enqueuePacket(pack(26, int16le(280), 0));
        log("Queued bytes=" + image.length + " lines=" + (image.length / WIDTH_BYTES));
        drainQueue();
    }

    private byte[] renderText(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(24);
        List<String> lines = wrapText(text, paint, WIDTH_PX - 32);
        int lineHeight = 34;
        int height = Math.max(164, 28 + lines.size() * lineHeight);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH_PX, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        int y = 24;
        for (String line : lines) {
            canvas.drawText(line, 16, y, paint);
            y += lineHeight;
        }
        return encodeBitmap(bitmap);
    }

    private List<String> wrapText(String text, Paint paint, int maxWidth) {
        String normalized = text.replace("\r\n", "\n").replace("|", " | ");
        List<String> lines = new ArrayList<>();
        for (String paragraph : normalized.split("\n")) {
            String current = "";
            for (int i = 0; i < paragraph.length(); i++) {
                String next = current + paragraph.charAt(i);
                if (paint.measureText(next) > maxWidth && current.length() > 0) {
                    lines.add(current);
                    current = String.valueOf(paragraph.charAt(i));
                } else {
                    current = next;
                }
            }
            if (current.length() > 0) lines.add(current);
        }
        return lines.isEmpty() ? java.util.Collections.singletonList(" ") : lines;
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
        CRC32 crc = new CRC32();
        crc.update(int32le(crcKey));
        crc.reset();
        return crc32WithSeed(data, crcKey);
    }

    private int crc32WithSeed(byte[] data, int seed) {
        int crc = seed ^ 0xffffffff;
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

    private void enqueuePacket(byte[] packet) {
        writeQueue.add(packet);
    }

    private void drainQueue() {
        if (writing || writeQueue.isEmpty() || writeCharacteristic == null || gatt == null) return;
        byte[] packet = writeQueue.poll();
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        writeCharacteristic.setValue(packet);
        writing = true;
        boolean ok = gatt.writeCharacteristic(writeCharacteristic);
        if (!ok) {
            writing = false;
            log("writeCharacteristic returned false");
        }
    }

    private boolean ready() {
        if (gatt == null || writeCharacteristic == null) {
            log("Connect first.");
            return false;
        }
        return true;
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            return device.getName() == null ? "" : device.getName();
        } catch (SecurityException e) {
            return "";
        }
    }

    private void log(String message) {
        handler.post(() -> status.setText(message + "\n" + status.getText()));
    }
}
