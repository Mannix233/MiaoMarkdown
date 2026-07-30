const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const printHtml = fs.readFileSync(
  path.join(root, "app", "src", "main", "assets", "print.html"),
  "utf8",
);
const editor = fs.readFileSync(
  path.join(root, "app", "src", "main", "java", "com", "paperang", "mdprint", "EditorActivity.java"),
  "utf8",
);

assert.match(printHtml, /bm:\s*\['\\\\boldsymbol\{#1\}',\s*1\]/);
assert.match(printHtml, /stroke-width:\s*0\.2px/);
assert.match(printHtml, /strong mjx-container\[jax="SVG"\]/);
assert.match(printHtml, /const minimum = hasExplicitMinimum \? minimumScale : 0\.55/);
assert.match(editor, /addTool\("粗体", this::toggleBold\)/);
assert.match(editor, /"\\\\boldsymbol\{"/);
assert.match(editor, /findEnclosingMath\(/);
assert.doesNotMatch(editor, /addTool\("粗体", \(\) -> toggleWrap\("\*\*"/);

console.log("Android formula bold source checks passed");
