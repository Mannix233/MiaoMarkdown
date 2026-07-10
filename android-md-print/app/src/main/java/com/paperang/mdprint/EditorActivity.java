package com.paperang.mdprint;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class EditorActivity extends Activity {
    public static final String EXTRA_MARKDOWN = "markdown";
    private static final String STATE_MARKDOWN = "state_markdown";
    private static final int HISTORY_DELAY_MS = 450;
    private static final int COLOR_INK = Color.rgb(25, 30, 29);
    private static final int COLOR_ACCENT = Color.rgb(25, 112, 86);
    private static final int COLOR_PAPER = Color.rgb(255, 255, 252);
    private static final int COLOR_CANVAS = Color.rgb(238, 241, 239);
    private static final int COLOR_LINE = Color.rgb(190, 199, 195);
    private static final String[] FONT_BLOCKS = {"font-sm", "font-md", "font-lg", "font-xl"};
    private static final String[] ALIGN_BLOCKS = {"left", "center", "right", "justify"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Deque<EditorState> undoStack = new ArrayDeque<>();
    private final Deque<EditorState> redoStack = new ArrayDeque<>();
    private final Runnable historyRunnable = this::commitPendingTyping;

    private EditText editor;
    private LinearLayout toolRow;
    private TextView documentStatus;
    private String lastSnapshot = "";
    private int lastSnapshotStart = 0;
    private int lastSnapshotEnd = 0;
    private boolean changingText = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi(savedInstanceState);
        handler.postDelayed(this::showKeyboard, 220);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MARKDOWN, editor == null ? "" : editor.getText().toString());
    }

    private void buildUi(Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(10));
        root.setBackgroundColor(COLOR_CANVAS);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(10), dp(10));
        header.setBackground(cardBackground(COLOR_INK, 0, 7));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("编辑文稿", 19, Color.WHITE, true);
        TextView subtitle = label("Markdown / HTML / LaTeX", 11, Color.rgb(192, 204, 199), false);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        Button done = commandButton("完成");
        done.setTextColor(COLOR_INK);
        done.setBackground(cardBackground(Color.rgb(243, 196, 72), Color.rgb(243, 196, 72), 6));
        done.setOnClickListener(v -> finishWithText());
        header.addView(done, new LinearLayout.LayoutParams(-2, dp(46)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setPadding(0, dp(9), 0, dp(6));
        Button textTools = categoryButton("文字");
        Button paragraphTools = categoryButton("段落");
        Button insertTools = categoryButton("插入");
        categoryRow.addView(textTools, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        categoryParams.setMargins(dp(7), 0, 0, 0);
        categoryRow.addView(paragraphTools, categoryParams);
        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        insertParams.setMargins(dp(7), 0, 0, 0);
        categoryRow.addView(insertTools, insertParams);
        root.addView(categoryRow);

        HorizontalScrollView toolsScroller = new HorizontalScrollView(this);
        toolsScroller.setHorizontalScrollBarEnabled(false);
        toolsScroller.setFillViewport(true);
        toolsScroller.setBackground(cardBackground(Color.WHITE, COLOR_LINE, 6));
        toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.setPadding(dp(7), dp(6), dp(7), dp(6));
        toolsScroller.addView(toolRow, new HorizontalScrollView.LayoutParams(-2, dp(52)));
        root.addView(toolsScroller, new LinearLayout.LayoutParams(-1, dp(54)));

        documentStatus = label("", 11, Color.rgb(74, 83, 80), false);
        documentStatus.setGravity(Gravity.CENTER_VERTICAL);
        documentStatus.setPadding(dp(3), dp(5), dp(3), dp(5));
        root.addView(documentStatus, new LinearLayout.LayoutParams(-1, dp(28)));

        editor = new EditText(this);
        String initial = savedInstanceState == null
                ? getIntent().getStringExtra(EXTRA_MARKDOWN)
                : savedInstanceState.getString(STATE_MARKDOWN);
        editor.setText(initial == null ? "" : initial);
        editor.setTextSize(17);
        editor.setTextColor(COLOR_INK);
        editor.setHintTextColor(Color.rgb(135, 143, 140));
        editor.setHint("在这里写 Markdown...");
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setVerticalScrollBarEnabled(true);
        editor.setPadding(dp(18), dp(16), dp(18), dp(120));
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editor.setBackground(cardBackground(COLOR_PAPER, COLOR_LINE, 6));
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateDocumentStatus();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (changingText) return;
                redoStack.clear();
                handler.removeCallbacks(historyRunnable);
                handler.postDelayed(historyRunnable, HISTORY_DELAY_MS);
            }
        });
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.setMargins(0, dp(4), 0, 0);
        root.addView(editor, editorParams);

        textTools.setOnClickListener(v -> {
            showTextTools();
            selectCategory(textTools, paragraphTools, insertTools);
        });
        paragraphTools.setOnClickListener(v -> {
            showParagraphTools();
            selectCategory(paragraphTools, textTools, insertTools);
        });
        insertTools.setOnClickListener(v -> {
            showInsertTools();
            selectCategory(insertTools, textTools, paragraphTools);
        });

        setContentView(root);
        editor.setSelection(editor.length());
        lastSnapshot = editor.getText().toString();
        lastSnapshotStart = editor.getSelectionStart();
        lastSnapshotEnd = editor.getSelectionEnd();
        updateDocumentStatus();
        showTextTools();
        selectCategory(textTools, paragraphTools, insertTools);
    }

    private void showTextTools() {
        clearTools();
        addTool("粗体", () -> toggleWrap("**", "**", "文字"));
        addTool("斜体", () -> toggleWrap("*", "*", "文字"));
        addTool("下划线", () -> toggleWrap("<u>", "</u>", "文字"));
        addTool("删除线", () -> toggleWrap("~~", "~~", "文字"));
        addTool("行内代码", () -> toggleWrap("`", "`", "code"));
        addTool("高亮", () -> toggleWrap("<mark>", "</mark>", "文字"));
        addTool("下标", () -> toggleWrap("<sub>", "</sub>", "2"));
        addTool("上标", () -> toggleWrap("<sup>", "</sup>", "2"));
        addTool("小字", () -> wrapBlock("font-sm", "文字"));
        addTool("正文", () -> wrapBlock("font-md", "文字"));
        addTool("大字", () -> wrapBlock("font-lg", "文字"));
        addTool("特大", () -> wrapBlock("font-xl", "文字"));
    }

    private void showParagraphTools() {
        clearTools();
        addTool("标题 1", () -> applyHeading(1));
        addTool("标题 2", () -> applyHeading(2));
        addTool("标题 3", () -> applyHeading(3));
        addTool("引用", () -> prefixSelectedLines("> ", false));
        addTool("项目符号", () -> prefixSelectedLines("- ", false));
        addTool("编号", () -> prefixSelectedLines("", true));
        addTool("任务", () -> prefixSelectedLines("- [ ] ", false));
        addTool("左对齐", () -> wrapBlock("left", "段落"));
        addTool("居中", () -> wrapBlock("center", "段落"));
        addTool("右对齐", () -> wrapBlock("right", "段落"));
        addTool("两端对齐", () -> wrapBlock("justify", "段落"));
    }

    private void showInsertTools() {
        clearTools();
        addDirectTool("撤销", this::undo);
        addDirectTool("重做", this::redo);
        addTool("链接", () -> insertLink(false));
        addTool("图片", () -> insertLink(true));
        addTool("表格", () -> insertAtCursor(
                "\n| 项目 | 说明 | 状态 |\n| --- | --- | --- |\n| A | 内容 | 完成 |\n"));
        addTool("行内公式", () -> toggleWrap("$", "$", "E=mc^2"));
        addTool("块公式", () -> toggleWrap("$$\n", "\n$$", "\\int_0^\\infty f(x)\\,dx"));
        addTool("代码块", () -> toggleWrap("```\n", "\n```", "code"));
        addTool("分隔线", () -> insertAtCursor("\n\n---\n\n"));
        addTool("换行", () -> insertAtCursor("  \n"));
    }

    private void clearTools() {
        toolRow.removeAllViews();
    }

    private void addTool(String text, Runnable action) {
        Button button = commandButton(text);
        button.setOnClickListener(v -> runEditCommand(action));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(0, 0, dp(7), 0);
        toolRow.addView(button, params);
    }

    private void addDirectTool(String text, Runnable action) {
        Button button = commandButton(text);
        button.setOnClickListener(v -> {
            finishImeComposition();
            action.run();
            editor.requestFocus();
            updateDocumentStatus();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(0, 0, dp(7), 0);
        toolRow.addView(button, params);
    }

    private Button categoryButton(String text) {
        Button button = commandButton(text);
        button.setTextSize(13);
        return button;
    }

    private Button commandButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(COLOR_INK);
        button.setPadding(dp(13), 0, dp(13), 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setBackground(cardBackground(Color.WHITE, COLOR_LINE, 6));
        return button;
    }

    private void selectCategory(Button selected, Button otherOne, Button otherTwo) {
        selected.setTextColor(Color.WHITE);
        selected.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        selected.setBackground(cardBackground(COLOR_ACCENT, COLOR_ACCENT, 6));
        for (Button button : new Button[]{otherOne, otherTwo}) {
            button.setTextColor(COLOR_INK);
            button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            button.setBackground(cardBackground(Color.WHITE, COLOR_LINE, 6));
        }
    }

    private void runEditCommand(Runnable action) {
        commitPendingTyping();
        String before = editor.getText().toString();
        int oldStart = selectionStart();
        int oldEnd = selectionEnd();
        finishImeComposition();
        editor.beginBatchEdit();
        changingText = true;
        try {
            action.run();
        } finally {
            changingText = false;
            editor.endBatchEdit();
        }
        String after = editor.getText().toString();
        if (!after.equals(before)) {
            undoStack.push(new EditorState(before, oldStart, oldEnd));
            trimHistory(undoStack);
            redoStack.clear();
            lastSnapshot = after;
            lastSnapshotStart = editor.getSelectionStart();
            lastSnapshotEnd = editor.getSelectionEnd();
        } else {
            editor.setSelection(oldStart, oldEnd);
        }
        editor.requestFocus();
        updateDocumentStatus();
    }

    private void toggleWrap(String before, String after, String placeholder) {
        Editable text = editor.getText();
        int start = selectionStart();
        int end = selectionEnd();
        String selected = text.subSequence(start, end).toString();
        if (selected.startsWith(before)
                && selected.endsWith(after)
                && selected.length() >= before.length() + after.length()) {
            String inner = selected.substring(before.length(), selected.length() - after.length());
            text.replace(start, end, inner);
            editor.setSelection(start, start + inner.length());
            return;
        }

        if (start >= before.length()
                && end + after.length() <= text.length()
                && text.subSequence(start - before.length(), start).toString().equals(before)
                && text.subSequence(end, end + after.length()).toString().equals(after)) {
            text.delete(end, end + after.length());
            text.delete(start - before.length(), start);
            editor.setSelection(start - before.length(), end - before.length());
            return;
        }

        String oppositeBefore = before.equals("<sub>") ? "<sup>" : (before.equals("<sup>") ? "<sub>" : "");
        String oppositeAfter = after.equals("</sub>") ? "</sup>" : (after.equals("</sup>") ? "</sub>" : "");
        if (!oppositeBefore.isEmpty()
                && start >= oppositeBefore.length()
                && end + oppositeAfter.length() <= text.length()
                && text.subSequence(start - oppositeBefore.length(), start).toString().equals(oppositeBefore)
                && text.subSequence(end, end + oppositeAfter.length()).toString().equals(oppositeAfter)) {
            int outerStart = start - oppositeBefore.length();
            int outerEnd = end + oppositeAfter.length();
            String inner = text.subSequence(start, end).toString();
            text.replace(outerStart, outerEnd, before + inner + after);
            int innerStart = outerStart + before.length();
            editor.setSelection(innerStart, innerStart + inner.length());
            return;
        }

        String conflict = before.equals("`") ? "$" : (before.equals("$") ? "`" : "");
        if (!conflict.isEmpty()
                && start >= conflict.length()
                && end + conflict.length() <= text.length()
                && text.subSequence(start - conflict.length(), start).toString().equals(conflict)
                && text.subSequence(end, end + conflict.length()).toString().equals(conflict)) {
            android.widget.Toast.makeText(
                    this,
                    "行内代码和公式不能直接嵌套，请先取消原格式",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String inner = selected.isEmpty() ? placeholder : selected;
        text.replace(start, end, before + inner + after);
        int innerStart = start + before.length();
        editor.setSelection(innerStart, innerStart + inner.length());
    }

    private void wrapBlock(String blockName, String placeholder) {
        Editable text = editor.getText();
        int selectionStart = selectionStart();
        int selectionEnd = selectionEnd();
        String[] group = blockName.startsWith("font-") ? FONT_BLOCKS : ALIGN_BLOCKS;
        BlockContainer existing = findSelectionContainer(text.toString(), group, selectionStart, selectionEnd);
        if (existing != null) {
            if (existing.name.equals(blockName)) {
                android.widget.Toast.makeText(this, "已经是这个格式，没有重复添加", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String opening = "::: " + blockName;
            int offset = opening.length() - (existing.openEnd - existing.openStart);
            boolean selectedWholeContainer = selectionStart == existing.openStart
                    && selectionEnd == existing.closeEnd;
            text.replace(existing.openStart, existing.openEnd, opening);
            if (selectedWholeContainer) {
                editor.setSelection(existing.contentStart + offset, existing.contentEnd + offset);
            } else {
                editor.setSelection(selectionStart + offset, selectionEnd + offset);
            }
            return;
        }

        int start = lineStart(selectionStart);
        int end = selectedBlockEnd();
        String original = text.subSequence(start, end).toString();
        String selected = original;
        if (selected.trim().isEmpty()) selected = placeholder;
        String replacement = "::: " + blockName + "\n" + selected + "\n:::";
        text.replace(start, end, replacement);
        int contentStart = start + blockName.length() + 5;
        if (original.isEmpty()) {
            editor.setSelection(contentStart, contentStart + selected.length());
        } else {
            editor.setSelection(contentStart + selectionStart - start, contentStart + selectionEnd - start);
        }
    }

    private BlockContainer findSelectionContainer(String source, String[] group, int start, int end) {
        List<BlockContainer> containers = parseBlockContainers(source);
        BlockContainer best = null;
        for (BlockContainer container : containers) {
            if (!contains(group, container.name)) continue;
            boolean containsSelection = start >= container.contentStart && end <= container.contentEnd;
            boolean selectsWholeContainer = start == container.openStart && end == container.closeEnd;
            if (!containsSelection && !selectsWholeContainer) continue;
            if (best == null
                    || container.closeEnd - container.openStart < best.closeEnd - best.openStart) {
                best = container;
            }
        }
        return best;
    }

    private List<BlockContainer> parseBlockContainers(String source) {
        List<BlockContainer> containers = new ArrayList<>();
        Deque<BlockContainer> stack = new ArrayDeque<>();
        int lineStart = 0;
        while (lineStart <= source.length()) {
            int nextBreak = source.indexOf('\n', lineStart);
            int lineEnd = nextBreak < 0 ? source.length() : nextBreak;
            String line = source.substring(lineStart, lineEnd).trim();
            if (line.startsWith("::: ") && line.length() > 4) {
                stack.push(new BlockContainer(
                        line.substring(4).trim(),
                        lineStart,
                        lineEnd,
                        nextBreak < 0 ? lineEnd : lineEnd + 1));
            } else if (line.equals(":::") && !stack.isEmpty()) {
                BlockContainer container = stack.pop();
                container.contentEnd = lineStart > 0 && source.charAt(lineStart - 1) == '\n'
                        ? lineStart - 1
                        : lineStart;
                container.closeEnd = lineEnd;
                containers.add(container);
            }
            if (nextBreak < 0) break;
            lineStart = nextBreak + 1;
        }
        return containers;
    }

    private boolean contains(String[] values, String target) {
        for (String value : values) {
            if (value.equals(target)) return true;
        }
        return false;
    }

    private void applyHeading(int level) {
        int start = lineStart(selectionStart());
        int end = selectedBlockEnd();
        String selected = editor.getText().subSequence(start, end).toString();
        String prefix = repeat("#", level) + " ";
        String[] lines = selected.split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String clean = lines[i].replaceFirst("^\\s{0,3}#{1,6}\\s+", "");
            if (!clean.isEmpty()) replacement.append(prefix);
            replacement.append(clean);
            if (i < lines.length - 1) replacement.append('\n');
        }
        editor.getText().replace(start, end, replacement.toString());
        editor.setSelection(start, start + replacement.length());
    }

    private void prefixSelectedLines(String prefix, boolean numbered) {
        int start = lineStart(selectionStart());
        int end = selectedBlockEnd();
        String selected = editor.getText().subSequence(start, end).toString();
        String[] lines = selected.split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        int listNumber = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isEmpty()) {
                String clean = line.replaceFirst("^\\s*(?:[-+*]\\s+(?:\\[[ xX]\\]\\s+)?|\\d+[.)]\\s+|>\\s+)", "");
                replacement.append(numbered ? (listNumber++) + ". " : prefix).append(clean);
            }
            if (i < lines.length - 1) replacement.append('\n');
        }
        editor.getText().replace(start, end, replacement.toString());
        editor.setSelection(start, start + replacement.length());
    }

    private void insertLink(boolean image) {
        int start = selectionStart();
        int end = selectionEnd();
        String selected = editor.getText().subSequence(start, end).toString();
        String label = selected.isEmpty() ? (image ? "图片说明" : "链接文字") : selected;
        String prefix = image ? "![" : "[";
        String replacement = prefix + label + "](https://)";
        editor.getText().replace(start, end, replacement);
        int urlStart = start + prefix.length() + label.length() + 2;
        editor.setSelection(urlStart, urlStart + "https://".length());
    }

    private void insertAtCursor(String value) {
        int start = selectionStart();
        int end = selectionEnd();
        editor.getText().replace(start, end, value);
        editor.setSelection(start + value.length());
    }

    private int selectionStart() {
        return Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
    }

    private int selectionEnd() {
        return Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
    }

    private int lineStart(int position) {
        Editable text = editor.getText();
        int result = Math.min(position, text.length());
        while (result > 0 && text.charAt(result - 1) != '\n') result--;
        return result;
    }

    private int lineEnd(int position) {
        Editable text = editor.getText();
        int result = Math.min(position, text.length());
        while (result < text.length() && text.charAt(result) != '\n') result++;
        return result;
    }

    private int selectedBlockEnd() {
        int start = selectionStart();
        int end = selectionEnd();
        if (end > start && end > 0 && editor.getText().charAt(end - 1) == '\n') {
            end--;
        }
        return lineEnd(end);
    }

    private void finishImeComposition() {
        BaseInputConnection.removeComposingSpans(editor.getText());
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.restartInput(editor);
    }

    private void commitPendingTyping() {
        handler.removeCallbacks(historyRunnable);
        if (editor == null || changingText) return;
        String current = editor.getText().toString();
        if (!current.equals(lastSnapshot)) {
            undoStack.push(new EditorState(lastSnapshot, lastSnapshotStart, lastSnapshotEnd));
            trimHistory(undoStack);
            redoStack.clear();
            lastSnapshot = current;
            lastSnapshotStart = editor.getSelectionStart();
            lastSnapshotEnd = editor.getSelectionEnd();
        }
    }

    private void undo() {
        commitPendingTyping();
        if (undoStack.isEmpty()) return;
        redoStack.push(currentEditorState());
        replaceAllText(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(currentEditorState());
        replaceAllText(redoStack.pop());
    }

    private EditorState currentEditorState() {
        return new EditorState(editor.getText().toString(), selectionStart(), selectionEnd());
    }

    private void replaceAllText(EditorState state) {
        changingText = true;
        editor.setText(state.text);
        int start = Math.min(state.selectionStart, editor.length());
        int end = Math.min(state.selectionEnd, editor.length());
        editor.setSelection(start, end);
        changingText = false;
        lastSnapshot = state.text;
        lastSnapshotStart = start;
        lastSnapshotEnd = end;
    }

    private void trimHistory(Deque<EditorState> stack) {
        while (stack.size() > 60) stack.removeLast();
    }

    private void updateDocumentStatus() {
        if (editor == null || documentStatus == null) return;
        int cursor = Math.max(0, editor.getSelectionStart());
        String before = editor.getText().subSequence(0, Math.min(cursor, editor.length())).toString();
        int line = before.isEmpty() ? 1 : before.split("\n", -1).length;
        documentStatus.setText("第 " + line + " 行  ·  " + editor.length() + " 字符");
    }

    @Override
    public void onBackPressed() {
        finishWithText();
    }

    private void finishWithText() {
        commitPendingTyping();
        Intent result = new Intent();
        result.putExtra(EXTRA_MARKDOWN, editor.getText().toString());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showKeyboard() {
        editor.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable cardBackground(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static class BlockContainer {
        final String name;
        final int openStart;
        final int openEnd;
        final int contentStart;
        int contentEnd;
        int closeEnd;

        BlockContainer(String name, int openStart, int openEnd, int contentStart) {
            this.name = name;
            this.openStart = openStart;
            this.openEnd = openEnd;
            this.contentStart = contentStart;
        }
    }

    private static class EditorState {
        final String text;
        final int selectionStart;
        final int selectionEnd;

        EditorState(String text, int selectionStart, int selectionEnd) {
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }
}
