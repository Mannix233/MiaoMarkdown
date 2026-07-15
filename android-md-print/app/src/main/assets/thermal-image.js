function installThermalImageProcessor(root) {
  const WINDOW_SIZE = 25;
  const SAUVOLA_K = 0.1;
  const DYNAMIC_RANGE = 128;

  function waitForImage(image) {
    if (image.complete) {
      if (image.naturalWidth > 0) return Promise.resolve();
      return Promise.reject(new Error("Image failed to load"));
    }
    return new Promise((resolve, reject) => {
      image.addEventListener("load", resolve, { once: true });
      image.addEventListener("error", reject, { once: true });
    });
  }

  function sauvolaBinarize(imageData) {
    const { data, width, height } = imageData;
    const gray = new Float32Array(width * height);
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const pixel = (y * width + x) * 4;
        const value = data[pixel] * 0.2126 + data[pixel + 1] * 0.7152 + data[pixel + 2] * 0.0722;
        gray[y * width + x] = value;
      }
    }

    const radius = Math.floor(WINDOW_SIZE / 2);
    const columnSum = new Float64Array(width);
    const columnSquared = new Float64Array(width);
    const updateRow = (row, direction) => {
      const offset = row * width;
      for (let x = 0; x < width; x += 1) {
        const value = gray[offset + x];
        columnSum[x] += value * direction;
        columnSquared[x] += value * value * direction;
      }
    };

    for (let y = 0; y < height; y += 1) {
      if (y === 0) {
        for (let row = 0; row <= Math.min(height - 1, radius); row += 1) updateRow(row, 1);
      } else {
        const addRow = y + radius;
        const removeRow = y - radius - 1;
        if (addRow < height) updateRow(addRow, 1);
        if (removeRow >= 0) updateRow(removeRow, -1);
      }

      const verticalCount = Math.min(height - 1, y + radius) - Math.max(0, y - radius) + 1;
      let sum = 0;
      let sumSquared = 0;
      for (let column = 0; column <= Math.min(width - 1, radius); column += 1) {
        sum += columnSum[column];
        sumSquared += columnSquared[column];
      }

      for (let x = 0; x < width; x += 1) {
        if (x > 0) {
          const addColumn = x + radius;
          const removeColumn = x - radius - 1;
          if (addColumn < width) {
            sum += columnSum[addColumn];
            sumSquared += columnSquared[addColumn];
          }
          if (removeColumn >= 0) {
            sum -= columnSum[removeColumn];
            sumSquared -= columnSquared[removeColumn];
          }
        }
        const horizontalCount = Math.min(width - 1, x + radius) - Math.max(0, x - radius) + 1;
        const count = horizontalCount * verticalCount;
        const mean = sum / count;
        const variance = Math.max(0, sumSquared / count - mean * mean);
        const deviation = Math.sqrt(variance);
        const threshold = mean * (1 + SAUVOLA_K * (deviation / DYNAMIC_RANGE - 1));
        const output = (y * width + x) * 4;
        const value = gray[y * width + x] < threshold ? 0 : 255;
        data[output] = value;
        data[output + 1] = value;
        data[output + 2] = value;
        data[output + 3] = 255;
      }
    }
  }

  async function processImage(image) {
    if (image.dataset.miaoThermalProcessed === "true") return false;
    await waitForImage(image);
    const rect = image.getBoundingClientRect();
    const width = Math.max(1, Math.round(rect.width));
    const height = Math.max(1, Math.round(rect.height));
    const canvas = image.ownerDocument.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    context.fillStyle = "#fff";
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);
    const imageData = context.getImageData(0, 0, width, height);
    sauvolaBinarize(imageData);
    context.putImageData(imageData, 0, 0);
    image.style.filter = "none";
    image.dataset.miaoThermalProcessed = "true";
    const replacementLoaded = new Promise((resolve, reject) => {
      image.addEventListener("load", resolve, { once: true });
      image.addEventListener("error", reject, { once: true });
    });
    image.src = canvas.toDataURL("image/png");
    await replacementLoaded;
    return true;
  }

  async function process(scope) {
    const images = [...scope.querySelectorAll("img")];
    let processedCount = 0;
    const warnings = [];
    for (const image of images) {
      try {
        if (await processImage(image)) processedCount += 1;
      } catch (error) {
        warnings.push((error && error.message) || "Thermal image processing failed");
      }
    }
    return { processedCount, warnings };
  }

  root.MiaoThermalImages = { process };
}

if (typeof window !== "undefined" && window.document) {
  installThermalImageProcessor(window);
}
