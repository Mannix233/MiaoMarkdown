const assert = require("node:assert/strict");
const path = require("node:path");
const { app, BrowserWindow } = require("electron");

const projectRoot = path.resolve(__dirname, "..");
app.setPath("userData", path.join(projectRoot, ".electron-test-data"));

const cases = [
  {
    name: "user control table",
    markdown: [
      "| | 时域方程 | 传递函数 |",
      "| --- | --- | --- |",
      "| 纯微分 | $y(t)=\\frac{dx(t)}{dt}$ | $G(s)=s$ |",
      "| 一阶微分 | $y(t)=\\tau\\frac{dx(t)}{dt}+x(t)$ | $G(s)=\\tau s+1$ |",
      "| 二阶微分 | $y(t)=\\tau^2\\frac{d^2x(t)}{dt^2}+2\\xi\\tau\\frac{dx(t)}{dt}+x(t)$ | $G(s)=\\tau^2s^2+2\\xi\\tau s+1$ |",
    ].join("\n"),
    minimumMath: 6,
  },
  {
    name: "calculus and limits",
    markdown: "$$\\int_0^\\infty e^{-st}f(t)\\,dt+\\sum_{k=0}^n a_k+\\lim_{s\\to0}sF(s)$$",
    minimumMath: 1,
  },
  {
    name: "matrices cases aligned",
    markdown: [
      "$$\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}$$",
      "$$f(x)=\\begin{cases}x^2,&x>0\\\\0,&x\\le0\\end{cases}$$",
      "$$\\begin{aligned}Y(s)&=G(s)R(s)\\\\y(0^+)&=\\lim_{s\\to\\infty}sY(s)\\end{aligned}$$",
    ].join("\n"),
    minimumMath: 3,
  },
  {
    name: "alternate delimiters and physics",
    markdown: "\\(E=mc^2\\)\n\n\\[\\bra{\\psi}\\hat H\\ket{\\psi}=E\\]",
    minimumMath: 2,
  },
  {
    name: "layout helpers html footnotes",
    markdown: [
      "::: center",
      "**居中粗体**",
      "",
      "- 列表内容",
      ":::",
      "",
      "::: font-xl",
      "<u>大号下划线</u> H<sub>2</sub>O",
      ":::",
      "",
      "脚注[^a]",
      "",
      "[^a]: 支持 Markdown 的脚注内容",
    ].join("\n"),
    minimumMath: 0,
  },
];

async function renderCase(window, testCase, requestId) {
  const source = JSON.stringify(testCase.markdown);
  return window.webContents.executeJavaScript(`
    renderMarkdown(${source}, 19, ${requestId}).then(function (height) {
      const content = document.getElementById('content');
      const bounds = content.getBoundingClientRect();
      const overflow = Array.from(content.querySelectorAll('*')).some(function (node) {
        const rect = node.getBoundingClientRect();
        return rect.right > bounds.right + 1 || rect.left < bounds.left - 1;
      });
      return {
        height: Number(height),
        rawResult: String(height),
        math: content.querySelectorAll('mjx-container').length,
        errors: content.querySelectorAll('[data-mml-node="merror"], mjx-merror').length,
        overflow,
        text: content.textContent,
        centered: Boolean(content.querySelector('.align-center')),
        large: Boolean(content.querySelector('.font-xl')),
        footnotes: Boolean(content.querySelector('.footnotes')),
        strongInsideCenter: Boolean(content.querySelector('.align-center strong')),
        listInsideCenter: Boolean(content.querySelector('.align-center li'))
      };
    })
  `);
}

app.whenReady().then(async () => {
  const window = new BrowserWindow({
    show: false,
    width: 384,
    height: 900,
    webPreferences: {
      contextIsolation: false,
      sandbox: false,
    },
  });

  try {
    await window.loadFile(path.join(projectRoot, "app", "src", "main", "assets", "print.html"));
    let requestId = 1;
    for (const testCase of cases) {
      const result = await renderCase(window, testCase, requestId++);
      assert.ok(Number.isFinite(result.height) && result.height > 0, `${testCase.name}: invalid height`);
      assert.ok(result.math >= testCase.minimumMath, `${testCase.name}: missing math nodes`);
      assert.equal(result.errors, 0, `${testCase.name}: MathJax reported an error`);
      assert.equal(result.overflow, false, `${testCase.name}: content exceeds the 384px print surface`);
      assert.equal(result.text.includes("Column 1"), false, `${testCase.name}: synthetic column label leaked`);
    }

    const helperResult = await renderCase(window, cases[4], requestId++);
    assert.equal(helperResult.centered, true, "center container was not rendered");
    assert.equal(helperResult.large, true, "font container was not rendered");
    assert.equal(helperResult.footnotes, true, "footnote section was not rendered");
    assert.equal(helperResult.strongInsideCenter, true, "Markdown bold was not parsed inside container");
    assert.equal(helperResult.listInsideCenter, true, "Markdown list was not parsed inside container");

    const codeResult = await renderCase(window, {
      name: "code protection",
      markdown: [
        "`\\\\(x_1+y\\\\)`",
        "",
        "```md",
        "\\\\[x_1+y\\\\]",
        "[^demo]: 这是代码，不是脚注",
        "```",
      ].join("\n"),
      minimumMath: 0,
    }, requestId++);
    assert.equal(codeResult.math, 0, "math delimiters inside code were rewritten");
    assert.equal(codeResult.footnotes, false, "footnote syntax inside code was rewritten");

    const longResult = await renderCase(window, {
      name: "long",
      markdown: Array.from({length: 700}, (_, index) => `第 ${index + 1} 行`).join("\n\n"),
      minimumMath: 0,
    }, requestId++);
    assert.ok(longResult.height > 12000, "long-document guard fixture did not cross 12000px");
    const shortResult = await renderCase(window, {
      name: "short after long",
      markdown: "短内容",
      minimumMath: 0,
    }, requestId++);
    assert.ok(shortResult.height < longResult.height / 4, "short render inherited the previous WebView height");

    const invalidResult = await window.webContents.executeJavaScript(
      `renderMarkdown('$$\\\\begin{definitelyUnknown}x\\\\end{definitelyUnknown}$$', 19, ${requestId++})`
    );
    assert.ok(String(invalidResult).startsWith("__ERROR__:"), "invalid formula was not blocked");

    const image = await window.webContents.capturePage();
    const bitmap = image.toBitmap();
    assert.ok(bitmap.some(value => value < 245), "captured preview is blank");
    console.log(`render smoke tests passed: ${cases.length + 4}`);
  } finally {
    window.destroy();
    app.quit();
  }
}).catch(error => {
  console.error(error);
  app.exit(1);
});
