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
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EditorActivity extends Activity {
    public static final String EXTRA_MARKDOWN = "markdown";

    private EditText editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi();
        new Handler(Looper.getMainLooper()).postDelayed(this::showKeyboard, 250);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(Color.rgb(234, 239, 235));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 14, 16, 14);
        header.setBackground(cardBackground(Color.rgb(22, 31, 29), 0, 8));

        TextView title = new TextView(this);
        title.setText("编辑 Markdown");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button done = new Button(this);
        done.setText("完成");
        done.setAllCaps(false);
        done.setTextColor(Color.rgb(22, 31, 29));
        done.setBackground(cardBackground(Color.WHITE, Color.rgb(180, 196, 188), 8));
        done.setOnClickListener(v -> finishWithText());
        header.addView(done, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        editor = new EditText(this);
        editor.setText(getIntent().getStringExtra(EXTRA_MARKDOWN));
        editor.setTextSize(17);
        editor.setTextColor(Color.rgb(22, 31, 29));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setMinLines(20);
        editor.setPadding(18, 18, 18, 18);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setBackground(cardBackground(Color.WHITE, Color.rgb(190, 203, 197), 8));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, 0, 1);
        editorParams.setMargins(0, 12, 0, 0);
        root.addView(editor, editorParams);

        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        finishWithText();
    }

    private void finishWithText() {
        Intent result = new Intent();
        result.putExtra(EXTRA_MARKDOWN, editor.getText().toString());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showKeyboard() {
        editor.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }
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
}
