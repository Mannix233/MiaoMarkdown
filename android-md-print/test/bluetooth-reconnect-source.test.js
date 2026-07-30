const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const manifest = fs.readFileSync(path.join(root, "app", "src", "main", "AndroidManifest.xml"), "utf8");
const activity = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "com", "paperang", "mdprint", "MainActivity.java"),
  "utf8",
);

assert.match(manifest, /android\.permission\.BLUETOOTH_SCAN/);
assert.match(manifest, /usesPermissionFlags="neverForLocation"/);
assert.match(activity, /classicConnect\.setOnClickListener\(v -> connectPrinter\(\)\)/);
assert.match(activity, /Manifest\.permission\.BLUETOOTH_SCAN/);
assert.match(activity, /PAPERANG_ADVERTISING_UUID/);
assert.match(activity, /PAPERANG_FF00_WRITE_UUID/);
assert.match(activity, /PAPERANG_FF00_STATUS_UUID/);
assert.match(activity, /MAX_BLE_SCAN_ATTEMPTS = 3/);
assert.match(activity, /BLE_GATT_CHUNK_BYTES = 20/);
assert.match(activity, /BLE_RASTER_FRAME_BYTES = WIDTH_BYTES \* 32/);
assert.match(activity, /onDescriptorWrite\(/);
assert.match(activity, /startBleProtocolProbe\(\)/);
assert.match(activity, /handleBleNotification\(copy\)/);
assert.match(activity, /command == COMMAND_BATTERY_STATUS && bleAwaitingBattery/);
assert.match(activity, /return classicOutput != null \|\| bleProtocolReady/);
assert.match(activity, /WRITE_TYPE_NO_RESPONSE/);
assert.match(activity, /Arrays\.copyOfRange\(packet, offset, end\)/);
assert.match(activity, /scheduleBleReconnect\(\)/);
assert.doesNotMatch(activity, /setDeviceStatus\("BLE 就绪", route\)/);
assert.doesNotMatch(
  activity,
  /classicConnect\.setOnClickListener\(v -> connectFirstPairedClassicDevice\(\)\)/,
);

console.log("Android Bluetooth reconnect source checks passed");
