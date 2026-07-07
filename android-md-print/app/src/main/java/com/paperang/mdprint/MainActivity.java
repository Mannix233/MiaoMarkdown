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
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
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

public class MainActivity extends Activity {
    private static final UUID PAPERANG_SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455");
    private static final UUID PAPERANG_WRITE_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3");
    private static final UUID PAPERANG_NOTIFY_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private static final int WIDTH_PX = 384;
    private static final int WIDTH_BYTES = 48;
    private static final int STANDARD_CRC_KEY = 0x35769521;
    private static final int SESSION_CRC_KEY = 0x06968634 ^ 0x002e696d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> bleWriteQueue = new ArrayDeque<>();
    private final ExecutorService classicWriter = Executors.newSingleThreadExecutor();
    private boolean bleWriting = false;
    private int crcKey = STANDARD_CRC_KEY;

    private EditText editor;
    private TextView status;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic bleWriteCharacteristic;
    private BluetoothLeScanner scanner;
    private BluetoothSocket classicSocket;
    private OutputStream classicOutput;

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
        editor.setText("# Print test\n\nItem | Meaning | Action\n--- | --- | ---\nF(s) | Input transform | Inverse Laplace\n\nShort lines first.");
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        Button sample = new Button(this);
        sample.setText("Short sample");
        sample.setOnClickListener(v -> editor.setText("Paperang P1 native Android test\nLine 2\nLine 3"));
        root.addView(sample);

        Button bleConnect = new Button(this);
        bleConnect.setText("BLE scan/connect");
        bleConnect.setOnClickListener(v -> startBleScan());
        root.addView(bleConnect);

        Button classicConnect = new Button(this);
        classicConnect.setText("Classic paired connect");
        classicConnect.setOnClickListener(v -> connectFirstPairedClassicDevice());
        root.addView(classicConnect);

        Button stripe = new Button(this);
        stripe.setText("Black stripe test");
        stripe.setOnClickListener(v -> printBlackStripe());
        root.addView(stripe);

        Button print = new Button(this);
        print.setText("Print text");
        print.setOnClickListener(v -> printText());
        root.addView(print);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(Color.rgb(40, 48, 48));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 280));

        setContentView(root);
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
            log("Bluetooth is disabled.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            log("No BLE scanner.");
            return;
        }
        log("BLE scanning...");
        try {
            scanner.startScan(scanCallback);
            handler.postDelayed(() -> {
                try {
                    scanner.stopScan(scanCallback);
                } catch (SecurityException ignored) {
                }
            }, 12000);
        } catch (SecurityException e) {
            log("BLE scan permission denied.");
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
                log("BLE found: " + name + " " + safeAddress(device));
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
        log("BLE connecting...");
        try {
            gatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            log("BLE connect permission denied.");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BLE connected. Discovering services...");
                try {
                    g.discoverServices();
                } catch (SecurityException e) {
                    log("BLE discover permission denied.");
                }
            } else {
                log("BLE disconnected: " + statusCode);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            BluetoothGattService service = g.getService(PAPERANG_SERVICE_UUID);
            if (service == null) {
                log("Paperang BLE service not found.");
                return;
            }
            bleWriteCharacteristic = service.getCharacteristic(PAPERANG_WRITE_UUID);
            if (bleWriteCharacteristic == null) {
                log("BLE write characteristic not found.");
                return;
            }
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
                    log("BLE notify permission denied.");
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
            log("BLE notify cmd=" + command);
        }
    };

    private void connectFirstPairedClassicDevice() {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            log("Bluetooth is disabled.");
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
                    log("No paired Paperang/Miao device. Pair it in Android Bluetooth settings first.");
                    return;
                }
                log("Classic connecting: " + safeDeviceName(target) + " " + safeAddress(target));
                closeBle();
                closeClassic();
                classicSocket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                adapter.cancelDiscovery();
                classicSocket.connect();
                classicOutput = classicSocket.getOutputStream();
                log("Classic connected.");
                initializePrinter();
            } catch (SecurityException e) {
                log("Classic Bluetooth permission denied.");
            } catch (IOException e) {
                log("Classic connect failed: " + e.getMessage());
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
        log("Printer initialized.");
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

    private void printText() {
        if (!ready()) return;
        sendImage(renderText(editor.getText().toString()));
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
        log("Queued image bytes=" + image.length + " lines=" + (image.length / WIDTH_BYTES));
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
        int y = 28;
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
            lines.add(current.length() == 0 ? " " : current);
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
        log("Connect with BLE or Classic first.");
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
